import asyncio

import httpx

from app.services.provider_resilience import (
    is_retryable_http_status,
    post_json_rpc_with_provider_failover,
    request_with_provider_failover,
    retry_delay_seconds,
)


def test_retry_delay_is_bounded_and_honors_retry_after():
    assert retry_delay_seconds(0, jitter_factor=1.0) == 0.5
    assert retry_delay_seconds(2, jitter_factor=1.0) == 2.0
    assert retry_delay_seconds(9, jitter_factor=1.0) == 8.0
    assert retry_delay_seconds(0, retry_after="30", jitter_factor=1.0) == 8.0
    assert retry_delay_seconds(0, retry_after="0", jitter_factor=1.0) == 0.25


def test_only_transient_http_statuses_trigger_provider_rotation():
    assert is_retryable_http_status(408)
    assert is_retryable_http_status(429)
    assert is_retryable_http_status(503)
    assert not is_retryable_http_status(400)
    assert not is_retryable_http_status(404)


def test_http_provider_failover_uses_second_endpoint_after_503():
    async def run():
        calls = []
        async def handler(request):
            calls.append(request.url.host)
            if request.url.host == "primary.example":
                return httpx.Response(503, request=request)
            return httpx.Response(200, json={"ok": True}, request=request)

        async def no_wait(_seconds):
            return None

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            response, provider = await request_with_provider_failover(
                client, "GET",
                ["https://primary.example/data", "https://backup.example/data"],
                sleep=no_wait, jitter=lambda: 1.0,
            )
        assert response.status_code == 200
        assert provider == "https://backup.example/data"
        assert calls == ["primary.example", "backup.example"]

    asyncio.run(run())


def test_json_rpc_provider_failover_rejects_rpc_error_payload():
    async def run():
        calls = []
        async def handler(request):
            calls.append(request.url.host)
            if request.url.host == "primary.example":
                return httpx.Response(200, json={"jsonrpc": "2.0", "error": {"code": -32000}}, request=request)
            return httpx.Response(200, json={"jsonrpc": "2.0", "result": "0x1"}, request=request)

        async def no_wait(_seconds):
            return None

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            result, provider = await post_json_rpc_with_provider_failover(
                client,
                ["https://primary.example", "https://backup.example"],
                {"jsonrpc": "2.0", "id": 1, "method": "eth_getBalance", "params": ["0xabc", "latest"]},
                sleep=no_wait, jitter=lambda: 1.0,
            )
        assert result["result"] == "0x1"
        assert provider == "https://backup.example"
        assert calls == ["primary.example", "backup.example"]

    asyncio.run(run())
