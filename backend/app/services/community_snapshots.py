from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from sqlalchemy import and_, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import CommunityContribution, CommunityProjectCatalogEntry, CommunityProjectRating

SHANGHAI = ZoneInfo("Asia/Shanghai")
WINDOW_DURATIONS: dict[str, timedelta | None] = {
    "overall": None,
    "7d": timedelta(days=7),
    "3d": timedelta(days=3),
    "1h": timedelta(hours=1),
}


def _iso(value: datetime) -> str:
    return value.isoformat(timespec="seconds")


def _number(value: Decimal | int | float | None) -> float:
    return float(value or 0)


def assemble_snapshot(
    window: str,
    generated_at: datetime,
    project_rows: list[dict],
    rating_rows: list[dict],
    asset_rows: list[dict] | None = None,
) -> dict:
    """Build a privacy-safe snapshot with USD valuations and token-separated native totals."""
    if window not in WINDOW_DURATIONS:
        raise ValueError(f"Unsupported community snapshot window: {window}")

    generated_at = generated_at.astimezone(timezone.utc)
    duration = WINDOW_DURATIONS[window]
    window_start = generated_at - duration if duration is not None else None

    ratings_by_project: dict[str, dict[int, int]] = {}
    for row in rating_rows:
        project_id = row["project_official_id"]
        score = int(row["score"])
        ratings_by_project.setdefault(project_id, {score: 0 for score in range(1, 6)})[score] = int(row["rating_count"])

    token_totals: dict[str, dict[tuple, dict]] = {}
    for row in asset_rows or []:
        project_id = row["project_official_id"]
        for asset in row.get("asset_breakdown") or []:
            if not isinstance(asset, dict):
                continue
            kind = asset.get("kind")
            symbol = str(asset.get("symbol") or "").strip().upper()
            if kind not in {"gas", "reward"} or not symbol:
                continue
            decimals = asset.get("decimals")
            key = (kind, symbol, decimals)
            try:
                native_amount = Decimal(str(asset.get("amount_native") or "0"))
            except (InvalidOperation, TypeError, ValueError):
                continue
            usd_amount = None
            if asset.get("usd_valuation") is not None:
                try:
                    usd_amount = Decimal(str(asset["usd_valuation"]))
                except (InvalidOperation, TypeError, ValueError):
                    # Keep a valid native amount even when its optional USD estimate is malformed.
                    usd_amount = None

            total = token_totals.setdefault(project_id, {}).setdefault(key, {
                "amount": Decimal("0"), "usd": Decimal("0"), "has_usd": False,
                "latest_timestamp": None, "sources": set(),
            })
            total["amount"] += native_amount
            if usd_amount is not None:
                total["usd"] += usd_amount
                total["has_usd"] = True
            stamp = asset.get("valuation_timestamp")
            if stamp and (total["latest_timestamp"] is None or str(stamp) > total["latest_timestamp"]):
                total["latest_timestamp"] = str(stamp)
            source = str(asset.get("valuation_source") or "").strip()
            if source:
                total["sources"].add(source)

    projects = []
    for row in project_rows:
        project_id = row["project_official_id"]
        distribution = ratings_by_project.get(project_id, {score: 0 for score in range(1, 6)})
        rating_count = sum(distribution.values())
        weighted_score = sum(score * count for score, count in distribution.items())
        average_rating = weighted_score / rating_count if rating_count else None
        gas = _number(row["gas_cost_usd"])
        reward = _number(row["actual_reward_usd"])
        created_at = row["created_at"].astimezone(timezone.utc)
        token_groups = token_totals.get(project_id, {})

        def token_rows(kind: str) -> list[dict]:
            values = []
            for (asset_kind, symbol, decimals), total in token_groups.items():
                if asset_kind != kind:
                    continue
                values.append({
                    "symbol": symbol,
                    "decimals": decimals,
                    "amount_native": format(total["amount"].normalize(), "f"),
                    "usd_valuation": float(total["usd"]) if total["has_usd"] else None,
                    "valuation_timestamp": total["latest_timestamp"],
                    "valuation_source": next(iter(total["sources"])) if len(total["sources"]) == 1 else ("mixed" if total["sources"] else None),
                })
            return sorted(values, key=lambda item: item["symbol"])

        projects.append({
            "project_official_id": project_id,
            "name": row["name"],
            "chain": row["chain"],
            "logo": row["logo"],
            "description": row["description"],
            "website_url": row["website_url"],
            "created_at": _iso(created_at),
            "tags": row.get("tags", []),
            "gas_cost_usd_valuation": gas,
            "actual_reward_usd_valuation": reward,
            "net_result_usd_valuation": reward - gas,
            "gas_by_token": token_rows("gas"),
            "reward_by_token": token_rows("reward"),
            "average_rating": average_rating,
            "rating_count": rating_count,
            "rating_distribution": {str(score): distribution.get(score, 0) for score in range(1, 6)},
        })

    return {
        "schema_version": 1,
        "window": window,
        "generated_at": _iso(generated_at),
        "update_time": _iso(generated_at.astimezone(SHANGHAI)),
        "window_start": _iso(window_start) if window_start else None,
        "window_end": _iso(generated_at),
        "metric_currency": "USD",
        "usd_values_are_valuations": True,
        "data_quality": "user_confirmed_actual_contributions_only",
        "projects": projects,
    }

async def generate_snapshot(db: AsyncSession, window: str, generated_at: datetime | None = None) -> dict:
    if window not in WINDOW_DURATIONS:
        raise ValueError(f"Unsupported community snapshot window: {window}")

    now = (generated_at or datetime.now(timezone.utc)).astimezone(timezone.utc)
    duration = WINDOW_DURATIONS[window]
    conditions = [
        CommunityContribution.project_official_id == CommunityProjectCatalogEntry.project_official_id,
        CommunityContribution.user_id.is_(None),
    ]
    if duration is not None:
        conditions.append(CommunityContribution.created_at >= now - duration)

    metrics_stmt = (
        select(
            CommunityProjectCatalogEntry.project_official_id,
            CommunityProjectCatalogEntry.name,
            CommunityProjectCatalogEntry.chain,
            CommunityProjectCatalogEntry.logo,
            CommunityProjectCatalogEntry.description,
            CommunityProjectCatalogEntry.website_url,
            CommunityProjectCatalogEntry.created_at.label("created_at"),
            func.coalesce(func.sum(CommunityContribution.gas_cost_usd), 0).label("gas_cost_usd"),
            func.coalesce(func.sum(CommunityContribution.actual_reward_usd), 0).label("actual_reward_usd"),
        )
        .select_from(CommunityProjectCatalogEntry)
        .outerjoin(CommunityContribution, and_(*conditions))
        .where(CommunityProjectCatalogEntry.is_active.is_(True))
        .group_by(
            CommunityProjectCatalogEntry.project_official_id,
            CommunityProjectCatalogEntry.name,
            CommunityProjectCatalogEntry.chain,
            CommunityProjectCatalogEntry.logo,
            CommunityProjectCatalogEntry.description,
            CommunityProjectCatalogEntry.website_url,
            CommunityProjectCatalogEntry.created_at,
            CommunityProjectCatalogEntry.sort_order,
        )
        .order_by(CommunityProjectCatalogEntry.sort_order.asc(), CommunityProjectCatalogEntry.name.asc())
    )
    metric_rows = (await db.execute(metrics_stmt)).mappings().all()
    tags_stmt = select(
        CommunityProjectCatalogEntry.project_official_id,
        CommunityProjectCatalogEntry.tags,
    ).where(CommunityProjectCatalogEntry.is_active.is_(True))
    tags_rows = (await db.execute(tags_stmt)).mappings().all()
    tags_by_project = {row["project_official_id"]: row["tags"] or [] for row in tags_rows}
    metric_rows = [
        {**dict(row), "tags": tags_by_project.get(row["project_official_id"], [])}
        for row in metric_rows
    ]

    ratings_stmt = (
        select(
            CommunityProjectRating.project_official_id,
            CommunityProjectRating.score,
            func.count(CommunityProjectRating.id).label("rating_count"),
        )
        .group_by(CommunityProjectRating.project_official_id, CommunityProjectRating.score)
    )
    rating_rows = (await db.execute(ratings_stmt)).mappings().all()



    asset_conditions = [CommunityContribution.user_id.is_(None), CommunityContribution.asset_breakdown.is_not(None)]
    if duration is not None:
        asset_conditions.append(CommunityContribution.created_at >= now - duration)
    asset_stmt = select(
        CommunityContribution.project_official_id,
        CommunityContribution.asset_breakdown,
    ).where(and_(*asset_conditions))
    asset_rows = (await db.execute(asset_stmt)).mappings().all()

    return assemble_snapshot(window, now, list(metric_rows), list(rating_rows), list(asset_rows))
