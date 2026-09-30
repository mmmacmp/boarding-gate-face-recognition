"""Download model files once.

- OpenCV Zoo YuNet + SFace (MIT / Apache-2.0) into model-service/models/ for MODEL=sface.
- InsightFace buffalo_l (weights non-commercial research only) into ~/.insightface/models/ for MODEL=r50.
Usage: python download_models.py
"""
import urllib.request
from pathlib import Path

from app.settings import DEFAULT_MODELS_DIR

OPENCV_ZOO = {
    "face_detection_yunet_2023mar.onnx": "https://huggingface.co/opencv/face_detection_yunet/resolve/main/",
    "face_recognition_sface_2021dec.onnx": "https://huggingface.co/opencv/face_recognition_sface/resolve/main/",
}


def main():
    """Fetch both model families into their respective default cache directories.

    OpenCV files that already exist are skipped without integrity checks. This
    script uses DEFAULT_MODELS_DIR, not the runtime MODELS_DIR override.
    InsightFace manages the buffalo_l cache through ensure_available.
    """
    DEFAULT_MODELS_DIR.mkdir(parents=True, exist_ok=True)
    for name, base in OPENCV_ZOO.items():
        target = DEFAULT_MODELS_DIR / name
        if target.exists():
            print("present:", target)
            continue
        print("downloading:", base + name)
        urllib.request.urlretrieve(base + name, target)
    from insightface.utils.storage import ensure_available
    print("buffalo_l at:", Path(ensure_available("models", "buffalo_l")))


if __name__ == "__main__":
    main()
