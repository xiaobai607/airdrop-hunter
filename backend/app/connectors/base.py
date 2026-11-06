from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime
from typing import Any


@dataclass(frozen=True)
class SourceRecord:
    """Normalized record returned by an explicitly supported data provider."""

    source: str
    external_id: str
    payload: dict[str, Any]
    fetched_at: datetime


class DataConnector(ABC):
    """Contract for provider integrations.

    Implementations must respect provider authentication, quotas, terms,
    robots/access policies, and commercial-use restrictions. Do not bypass
    CAPTCHAs, anti-bot controls, or account protections.
    """

    @property
    @abstractmethod
    def source_name(self) -> str:
        raise NotImplementedError

    @abstractmethod
    async def healthcheck(self) -> bool:
        raise NotImplementedError
