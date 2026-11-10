import asyncio
import random
import time
from datetime import timezone
from email.utils import parsedate_to_datetime
from typing import Any, Awaitable, Callable

import httpx

RETRYABLE_HTTP_STATUSES = {408, 425, 429}


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
    candidates = list(urls)[:max(1, max_attempts)]
    if not candidates:
        raise ValueError("No public provider endpoints configured")
    last_error: Exception | None = None
    for index, url in enumerate(candidates):
        try:
            response = await client.request(method, url, **kwargs)
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
            return response, url
        except httpx.TransportError as error:
            last_error = error
            if index >= len(candidates) - 1:
                raise
            await sleep(retry_delay_seconds(index, None, jitter()))
        except httpx.HTTPStatusError as error:
            if not is_retryable_http_status(error.response.status_code) or index >= len(candidates) - 1:
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
                raise ProviderResponseError("RPC provider returned an invalid result")
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
            last_error = error
            if index < len(candidates) - 1:
                await sleep(retry_delay_seconds(index, None, jitter()))
    if last_error:
        raise last_error
    raise ProviderResponseError("All public RPC providers failed")
