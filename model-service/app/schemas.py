"""Response contract of POST /v1/embed. The gate-service depends on these field names."""
from pydantic import BaseModel


class FaceOut(BaseModel):
    """Public detection fields; internal facial landmarks are intentionally excluded."""

    bbox: list[float]                    # x1, y1, x2, y2 in pixels
    det_score: float
    embedding: list[float] | None = None  # L2-normalised; only present when exactly one face was detected


class EmbedResponse(BaseModel):
    """Model identity, detected faces, and measured stages for one image request."""

    model_id: str                        # templates are only comparable within the same model_id
    dim: int
    faces: list[FaceOut]
    timings_ms: dict[str, float]


class HealthResponse(BaseModel):
    """Metadata for the backend initialized during application startup."""

    status: str
    model_id: str
    dim: int
    device: str                          # the device actually used, not just the one requested
    providers: list[str]
