from pathlib import Path

from alembic.config import Config
from alembic.script import ScriptDirectory


def test_migration_history_is_a_single_linear_chain() -> None:
    backend_dir = Path(__file__).resolve().parents[1]
    config = Config(str(backend_dir / "alembic.ini"))
    scripts = ScriptDirectory.from_config(config)

    assert scripts.get_current_head() == "0023_trusted_catalog_source"
    bootstrap = scripts.get_revision("0001_bootstrap")
    ledger = scripts.get_revision("0002_personal_ledger")
    nullable_reward = scripts.get_revision("0003_nullable_expected_reward")
    community = scripts.get_revision("0004_community_contributions")
    anonymize = scripts.get_revision("0005_anonymize_contributions")
    ratings = scripts.get_revision("0006_community_ratings")
    wallet_metadata = scripts.get_revision("0007_wallet_metadata")
    project_wallet_assignments = scripts.get_revision("0008_project_wallet_assignments")
    dynamic_catalog = scripts.get_revision("0009_dynamic_community_catalog")
    discovery_candidates = scripts.get_revision("0010_rss_candidates")
    candidate_review = scripts.get_revision("0011_candidate_review")
    public_metrics = scripts.get_revision("0012_public_metrics")
    currency_assets = scripts.get_revision("0013_currency_assets")
    discovery_source_status = scripts.get_revision("0014_discovery_source_status")
    community_favorites = scripts.get_revision("0015_community_favorites")
    nullable_expected_gas = scripts.get_revision("0016_nullable_expected_gas")
    verified_contributions = scripts.get_revision("0017_verified_contrib")
    idempotent_projects = scripts.get_revision("0018_idempotent_project_creation")
    dashboard_aggregates = scripts.get_revision("0019_comm_dash")
    airdrop_filters = scripts.get_revision("0020_airdrop_filters")
    airdrop_campaigns = scripts.get_revision("0021_airdrop_campaigns")
    campaign_heat_source = scripts.get_revision("0022_campaign_heat_source")
    trusted_catalog_source = scripts.get_revision("0023_trusted_catalog_source")
    assert bootstrap is not None and bootstrap.down_revision is None
    assert ledger is not None and ledger.down_revision == "0001_bootstrap"
    assert nullable_reward is not None and nullable_reward.down_revision == "0002_personal_ledger"
    assert community is not None and community.down_revision == "0003_nullable_expected_reward"
    assert anonymize is not None and anonymize.down_revision == "0004_community_contributions"
    assert ratings is not None and ratings.down_revision == "0005_anonymize_contributions"
    assert wallet_metadata is not None and wallet_metadata.down_revision == "0006_community_ratings"
    assert project_wallet_assignments is not None and project_wallet_assignments.down_revision == "0007_wallet_metadata"
    assert dynamic_catalog is not None and dynamic_catalog.down_revision == "0008_project_wallet_assignments"
    assert discovery_candidates is not None and discovery_candidates.down_revision == "0009_dynamic_community_catalog"
    assert candidate_review is not None and candidate_review.down_revision == "0010_rss_candidates"
    assert public_metrics is not None and public_metrics.down_revision == "0011_candidate_review"
    assert currency_assets is not None and currency_assets.down_revision == "0012_public_metrics"
    assert discovery_source_status is not None and discovery_source_status.down_revision == "0013_currency_assets"
    assert community_favorites is not None and community_favorites.down_revision == "0014_discovery_source_status"
    assert nullable_expected_gas is not None and nullable_expected_gas.down_revision == "0015_community_favorites"
    assert verified_contributions is not None and verified_contributions.down_revision == "0016_nullable_expected_gas"
    assert idempotent_projects is not None and idempotent_projects.down_revision == "0017_verified_contrib"
    assert dashboard_aggregates is not None and dashboard_aggregates.down_revision == "0018_idempotent_project_creation"
    assert airdrop_filters is not None and airdrop_filters.down_revision == "0019_comm_dash"
    assert airdrop_campaigns is not None and airdrop_campaigns.down_revision == "0020_airdrop_filters"
    assert campaign_heat_source is not None and campaign_heat_source.down_revision == "0021_airdrop_campaigns"
    assert trusted_catalog_source is not None and trusted_catalog_source.down_revision == "0022_campaign_heat_source"
    discovery_filter_decisions = scripts.get_revision("0024_discovery_filter_decisions")
    assert discovery_filter_decisions is not None
    assert discovery_filter_decisions.down_revision == "0023_trusted_catalog_source"
    assert scripts.get_current_head() == "0024_discovery_filter_decisions"
