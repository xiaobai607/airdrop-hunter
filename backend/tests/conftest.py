import pytest
from app.main import limiter


@pytest.fixture(autouse=True)
def disable_rate_limits_except_explicit_limit_tests():
    # Other API tests focus on their own behavior; the dedicated rate-limit test enables it.
    limiter.enabled = False
    yield
    limiter.enabled = False
