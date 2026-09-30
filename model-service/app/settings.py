"""Runtime switches, read once from environment variables (plain dataclass, no extra dependency)."""
import os
from dataclasses import dataclass
from pathlib import Path

DEFAULT_MODELS_DIR = Path(__file__).resolve().parents[1] / "models"  # OpenCV Zoo files for MODEL=sface


@dataclass(frozen=True)
class Settings:
    """Immutable configuration shared by the HTTP app and its selected backend."""

    device: str = "cpu"          # "cuda" | "cpu"
    model: str = "r50"           # "r50" (InsightFace, non-commercial weights) | "sface" (OpenCV Zoo, license-clean)
    cpu_threads: int = 0         # 0 = ONNX Runtime default (all cores); 4 = the "cpu4" edge proxy
    det_size: int = 640          # detector input size in pixels
    max_image_bytes: int = 5 * 1024 * 1024
    models_dir: Path = DEFAULT_MODELS_DIR
    min_face_px: int = 80        # faces narrower than this are ignored (background people outside the gate zone)

    def __post_init__(self):
        """Reject unknown device or model names even when Settings is built directly."""
        if self.device not in ("cuda", "cpu"):
            raise ValueError(f"DEVICE must be cuda or cpu, got {self.device!r}")
        if self.model not in ("r50", "sface"):
            raise ValueError(f"MODEL must be r50 or sface, got {self.model!r}")


def load_settings() -> Settings:
    """Snapshot environment variables, convert numeric values, and apply defaults.

    Called when app.main is imported; changing the environment afterward does
    not reconfigure an existing app or its loaded models.
    """
    return Settings(
        device=os.getenv("DEVICE", "cpu").lower(),
        model=os.getenv("MODEL", "r50").lower(),
        cpu_threads=int(os.getenv("CPU_THREADS", "0")),
        det_size=int(os.getenv("DET_SIZE", "640")),
        max_image_bytes=int(os.getenv("MAX_IMAGE_BYTES", str(5 * 1024 * 1024))),
        models_dir=Path(os.getenv("MODELS_DIR", str(DEFAULT_MODELS_DIR))),
        min_face_px=int(os.getenv("MIN_FACE_PX", "80")),
    )
