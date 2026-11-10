import asyncio
import random
import time
from datetime import timezone
from email.utils import parsedate_to_datetime
from typing import Any, Awaitable, Callable

import httpx

RETRYABLE_HTTP_STATUSES = {408, 425, 429}
_PROVIDER_COOLDOWNS: dict[str, float] = {}
_PROVIDER_COOLDOWN_SECONDS = 30.0


def _provider_candidates(urls: list[str] | tuple[str, ...], max_attempts: int) -> list[str]:
    now = time.monotonic()
    candidates = list(dict.fromkeys(urls))
    healthy = [url for url in candidates if _PROVIDER_COOLDOWNS.get(url, 0.0) <= now]
    if not healthy:
        healthy = sorted(candidates, key=lambda url: _PROVIDER_COOLDOWNS.get(url, 0.0))
    return healthy[:max(1, max_attempts)]


def _mark_provider_failure(url: str, retry_after: str | None = None) -> None:
    delay = retry_delay_seconds(0, retry_after, jitter_factor=1.0)
    _PROVIDER_COOLDOWNS[url] = time.monotonic() + max(_PROVIDER_COOLDOWN_SECONDS, delay)


def _mark_provider_success(url: str) -> None:
    _PROVIDER_COOLDOWNS.pop(url, None)


class ProviderResponseError(RuntimeError):
    pass


def is_retryable_http_status(status_code: int) -> bool:
    return status_code in RETRYABLE_HTTP_STATUSES or 500 <= status_code <= 599


def retry_delay_seconds(
    attempt: int,
    retry_after: str | None = None,
    jitter_factor: float = 1.0,
) -> float:
    requested = None
    if retry_after:
        try:
            requested = max(0.0, float(retry_after.strip()))
        except ValueError:
            try:
                retry_at = parsedate_to_datetime(retry_after)
                if retry_at.tzinfo is None:
                    retry_at = retry_at.replace(tzinfo=timezone.utc)
                requested = max(0.0, retry_at.timestamp() - time.time())
            except (TypeError, ValueError, OverflowError):
                requested = None
    base = min(0.5 * (2 ** min(max(attempt, 0), 4)), 8.0)
    delay = base if requested is None else min(requested, 8.0)
    return min(8.0, max(0.25, delay * min(1.2, max(0.8, jitter_factor))))


async def request_with_provider_failover(
    client: httpx.AsyncClient,
    method: str,
    urls: list[str] | tuple[str, ...],
    *,
    max_attempts: int = 3,
    sleep: Callable[[float], Awaitable[Any]] = asyncio.sleep,
    jitter: Callable[[], float] = lambda: random.uniform(0.8, 1.2),
    **kwargs: Any,
) -> tuple[httpx.Response, str]:
    candidates = _provider_candidates(urls, max_attempts)
    if not candidates:
        raise ValueError("No public provider endpoints configured")
    last_error: Exception | None = None
    for index, url in enumerate(candidates):
        try:
            response = await client.request(method, url, **kwargs)
            if is_retryable_http_status(response.status_code):
                _mark_provider_failure(url, response.headers.get("Retry-After"))
            if is_retryable_http_status(response.status_code) and index < len(candidates) - 1:
                last_error = httpx.HTTPStatusError(
                    f"Provider returned HTTP {response.status_code}",
                    request=response.request,
                    response=response,
                )
                await sleep(retry_delay_seconds(
                    index, response.headers.get("Retry-After"), jitter()
                ))
                continue
            response.raise_for_status()
            _mark_provider_success(url)
            return response, url
        except httpx.TransportError as error:
            _mark_provider_failure(url)
            last_error = error
            if index >= len(candidates) - 1:
                raise
            await sleep(retry_delay_seconds(index, None, jitter()))
        except httpx.HTTPStatusError as error:
            if not is_retryable_http_status(error.response.status_code):
                raise
            _mark_provider_failure(url, error.response.headers.get("Retry-After"))
            if index >= len(candidates) - 1:
                raise
            last_error = error
            await sleep(retry_delay_seconds(
                index, error.response.headers.get("Retry-After"), jitter()
            ))
    if last_error:
        raise last_error
    raise ProviderResponseError("All public providers failed")


async def post_json_rpc_with_provider_failover(
    client: httpx.AsyncClient,
    urls: list[str] | tuple[str, ...],
    payload: dict[str, Any],
    *,
    sleep: Callable[[float], Awaitable[Any]] = asyncio.sleep,
    jitter: Callable[[], float] = lambda: random.uniform(0.8, 1.2),
) -> tuple[dict[str, Any], str]:
    last_error: Exception | None = None
    candidates = list(urls)[:3]
    if not candidates:
        raise ValueError("No public RPC endpoints configured")
    for index, url in enumerate(candidates):
        try:
            response, used_url = await request_with_provider_failover(
                client, "POST", [url], max_attempts=1, json=payload, sleep=sleep, jitter=jitter
            )
            data = response.json()
            if not isinstance(data, dict) or data.get("error") is not None or "result" not in data:
                _mark_provider_failure(used_url)
                raise ProviderResponseError("RPC provider returned an invalid result")
            _mark_provider_success(used_url)
            return data, used_url
        except httpx.HTTPStatusError as error:
            if not is_retryable_http_status(error.response.status_code):
                raise
            last_error = error
            if index < len(candidates) - 1:
                await sleep(retry_delay_seconds(
                    index, error.response.headers.get("Retry-After"), jitter()
                ))
        except (httpx.TransportError, ValueError, ProviderResponseError) as error:
            _mark_provider_failure(url)
            last_error = error
            if index < len(candidates) - 1:
                await sleep(retry_delay_seconds(index, None, jitter()))
    if last_error:
        raise last_error
    raise ProviderResponseError("All public RPC providers failed")
