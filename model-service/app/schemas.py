"""Response contract of POST /v1/embed. The gate-service depends on these field names."""
from pydantic import BaseModel, Field


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
    small_faces: int = 0                 # faces ignored as narrower than MIN_FACE_PX (capture feedback: "step closer")
    timings_ms: dict[str, float]


class Faults(BaseModel):
    """Demo fault injection (the console's "model down" button). Not something a vendor SDK would expose."""

    down: bool = False
    latency_ms: int = Field(0, ge=0, le=30_000)


class HealthResponse(BaseModel):
    """Metadata for the backend initialized during application startup."""

    status: str
    model_id: str
    dim: int
    device: str                          # the device actually used, not just the one requested
    providers: list[str]
