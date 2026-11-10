from datetime import datetime
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")


def due_windows(now: datetime) -> list[str]:
    """Return snapshots due at this instant under the UTC+8 newspaper schedule."""
    local = now.astimezone(SHANGHAI)
    due = ["1h"]
    if local.minute == 0 and local.hour in (0, 6, 12, 18):
        due.append("3d")
    if local.hour == 8 and local.minute == 0:
        due.extend(("overall", "7d"))
    return due
