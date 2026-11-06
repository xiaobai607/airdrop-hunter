import asyncio
from fastapi import Request
from app.main import unexpected_exception_handler

def test_unhandled_exception_returns_safe_message():
    scope = {
        "type": "http", "asgi": {"version": "3.0"}, "http_version": "1.1",
        "method": "GET", "scheme": "http", "path": "/unexpected",
        "raw_path": b"/unexpected", "query_string": b"", "headers": [],
        "server": ("testserver", 80), "client": ("testclient", 123),
        "root_path": "",
    }
    response = asyncio.run(unexpected_exception_handler(Request(scope), RuntimeError("sensitive internal detail")))
    assert response.status_code == 500
    assert response.body == '{"detail":"服务器开小差了，请重试"}'.encode()
    assert b"sensitive internal detail" not in response.body
