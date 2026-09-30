"""Face detection + embedding backends. Stateless: no identities, nothing written to disk.

r50   = SCRFD-10G + ArcFace R50 (InsightFace buffalo_l). Code MIT, WEIGHTS NON-COMMERCIAL RESEARCH ONLY.
sface = YuNet + SFace (OpenCV Zoo). MIT / Apache-2.0, the license-clean mode.
"""
import logging
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

from .settings import Settings

log = logging.getLogger("model-service")


@dataclass
class Face:
    """Internal detection result, including alignment data hidden from the API."""

    bbox: list[float]      # x1, y1, x2, y2
    det_score: float
    landmarks: np.ndarray  # backend-specific data needed for alignment; never returned to callers


def _normalise(v: np.ndarray) -> np.ndarray:
    """Flatten a nonzero model feature vector and scale it to unit L2 length.

    Unit vectors allow downstream cosine comparison with a dot product. This
    assumes the recognizer returned a finite, nonzero vector; it does not check it.
    """
    v = v.astype(np.float32).ravel()
    return v / np.linalg.norm(v)


def _ort_session(path: Path, s: Settings) -> ort.InferenceSession:
    """Create an R50 inference session with explicit thread and device settings."""
    # We build ONNX Runtime sessions ourselves instead of insightface.model_zoo.get_model(), because get_model
    # ignores sess_options (InsightFace issue #2899), and we need intra_op_num_threads for the "cpu4" benchmark.
    opts = ort.SessionOptions()
    if s.cpu_threads:
        opts.intra_op_num_threads = s.cpu_threads
    providers = ["CUDAExecutionProvider", "CPUExecutionProvider"] if s.device == "cuda" else ["CPUExecutionProvider"]
    session = ort.InferenceSession(str(path), opts, providers=providers)
    if s.device == "cuda" and session.get_providers()[0] != "CUDAExecutionProvider":
        # Fail fast: a silent CPU fallback would make GPU latency numbers wrong.
        raise RuntimeError(f"DEVICE=cuda but ONNX Runtime fell back to {session.get_providers()} for {path.name}")
    return session


class R50Backend:
    """Adapt SCRFD detection and ArcFace recognition to the service interface."""

    model_id = "r50-buffalo_l"
    dim = 512

    def __init__(self, s: Settings):
        """Locate cached weights (downloading if needed) and prepare both models."""
        from insightface.model_zoo.arcface_onnx import ArcFaceONNX
        from insightface.model_zoo.scrfd import SCRFD
        from insightface.utils import face_align
        from insightface.utils.storage import ensure_available

        if s.device == "cuda":
            ort.preload_dlls()  # loads the CUDA/cuDNN DLLs shipped by the nvidia-* pip wheels
        root = Path(ensure_available("models", "buffalo_l"))  # downloads ~280 MB on first use
        det_path, rec_path = root / "det_10g.onnx", root / "w600k_r50.onnx"
        self._align = face_align.norm_crop
        self.detector = SCRFD(str(det_path), session=_ort_session(det_path, s))
        self.detector.prepare(ctx_id=0 if s.device == "cuda" else -1, input_size=(s.det_size, s.det_size))
        self.recognizer = ArcFaceONNX(str(rec_path), session=_ort_session(rec_path, s))
        self.device = s.device
        self.providers = self.recognizer.session.get_providers()

    def detect(self, img: np.ndarray) -> list[Face]:
        """Keep all detected faces so the API can skip embedding ambiguous images."""
        # max_num=0 avoids keeping only one face, which could hide bystanders.
        bboxes, kpss = self.detector.detect(img, max_num=0, metric="default")
        return [Face(bbox=[float(x) for x in b[:4]], det_score=float(b[4]), landmarks=k) for b, k in zip(bboxes, kpss)]

    def embed(self, img: np.ndarray, face: Face) -> np.ndarray:
        """Align by facial landmarks, then produce a unit-length 512-value vector."""
        crop = self._align(img, landmark=face.landmarks)  # 112x112 aligned face
        return _normalise(self.recognizer.get_feat(crop))

    def warmup(self):
        """Exercise both models; a blank detection frame alone would skip recognition."""
        self.detector.detect(np.zeros((480, 640, 3), np.uint8), max_num=0, metric="default")
        self.recognizer.get_feat(np.zeros((112, 112, 3), np.uint8))


class SFaceBackend:
    """Adapt OpenCV YuNet and SFace to the same interface, using CPU inference."""

    model_id = "sface-2021dec"
    dim = 128
    YUNET = "face_detection_yunet_2023mar.onnx"
    SFACE = "face_recognition_sface_2021dec.onnx"

    def __init__(self, s: Settings):
        """Load local OpenCV weights and record the device this backend actually uses."""
        yunet, sface = s.models_dir / self.YUNET, s.models_dir / self.SFACE
        if not (yunet.exists() and sface.exists()):
            raise FileNotFoundError(f"SFace/YuNet models missing in {s.models_dir}; run: python download_models.py")
        if s.device == "cuda":
            log.warning("MODEL=sface runs on CPU: OpenCV 5's DNN engine ignores CUDA targets")
        if s.cpu_threads:
            cv2.setNumThreads(s.cpu_threads)
        # score threshold 0.8, NMS 0.3, top-k 5000: the OpenCV Zoo defaults used in the Phase 3 smoke test
        self.detector = cv2.FaceDetectorYN.create(str(yunet), "", (320, 320), 0.8, 0.3, 5000)
        self.recognizer = cv2.FaceRecognizerSF.create(str(sface), "")
        self.device = "cpu"
        self.providers = ["opencv-dnn-cpu"]

    def detect(self, img: np.ndarray) -> list[Face]:
        """Convert YuNet rectangles to corner coordinates and retain alignment data."""
        h, w = img.shape[:2]
        self.detector.setInputSize((w, h))
        _, rows = self.detector.detect(img)
        if rows is None:
            return []
        # Each row: x, y, w, h, 5 landmark pairs, score. Keep the full row in
        # landmarks because OpenCV's alignCrop expects that complete detection.
        return [Face(bbox=[float(r[0]), float(r[1]), float(r[0] + r[2]), float(r[1] + r[3])],
                     det_score=float(r[14]), landmarks=r) for r in rows]

    def embed(self, img: np.ndarray, face: Face) -> np.ndarray:
        """Align using the stored YuNet row and return a unit-length 128-value vector."""
        crop = self.recognizer.alignCrop(img, face.landmarks)
        return _normalise(self.recognizer.feature(crop))

    def warmup(self):
        """Initialize detection and recognition paths using synthetic image arrays."""
        self.detect(np.zeros((480, 640, 3), np.uint8))
        self.recognizer.feature(np.zeros((112, 112, 3), np.uint8))


def load_backend(s: Settings):
    """Select the backend from Settings, which already validates the model name."""
    return R50Backend(s) if s.model == "r50" else SFaceBackend(s)
