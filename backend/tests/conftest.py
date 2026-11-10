import pytest
from app.main import app, limiter


@pytest.fixture(autouse=True)
def disable_rate_limits_except_explicit_limit_tests():
    # Other API tests focus on their own behavior; the dedicated rate-limit test enables it.
    limiter.enabled = False
    app.state.allow_registration = True
    yield
    app.state.allow_registration = False
    limiter.enabled = False
