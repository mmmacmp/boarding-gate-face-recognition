"""model-service: stateless face detection + embedding. Stands in for a vendor matcher SDK.

It never sees identities, never stores or logs images or embeddings. The gate-service owns every decision.
Run: uvicorn app.main:app --port 8001   (switches: DEVICE=cuda|cpu, MODEL=r50|sface, CPU_THREADS=4, MIN_FACE_PX=80)
"""
import logging
import threading
import time
from contextlib import asynccontextmanager

import cv2
import numpy as np
from fastapi import FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from prometheus_fastapi_instrumentator import Instrumentator

from .models import load_backend
from .schemas import EmbedResponse, FaceOut, HealthResponse
from .settings import Settings, load_settings

log = logging.getLogger("model-service")


def create_app(settings: Settings, backend=None) -> FastAPI:
    """Build an app with its own backend state and inference lock.

    Tests can inject a fake backend to exercise the HTTP contract without model
    files. Production uses the module-level app and loads models during startup.
    """
    state = {"backend": backend}
    lock = threading.Lock()  # detector objects are not thread-safe; one gate = one passenger at a time anyway

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        """Load and warm both model stages before the server accepts requests."""
        if state["backend"] is None:
            state["backend"] = load_backend(settings)
        b = state["backend"]
        # Warm-up before accepting traffic: the first CUDA runs take ~1 s (kernel/cuDNN set-up), which would
        # exceed the gate-service read timeout and send the first passenger to MANUAL.
        t = time.perf_counter()
        for _ in range(3):
            b.warmup()
        log.warning("warm-up took %.0f ms", (time.perf_counter() - t) * 1000)
        log.warning("model-service ready: model_id=%s device=%s providers=%s", b.model_id, b.device, b.providers)
        yield  # Startup ends here; FastAPI serves requests until shutdown resumes this context.

    app = FastAPI(title="poc model-service", lifespan=lifespan,
                  description="Stateless face embedding service for an interview POC. Not production-ready.")
    Instrumentator().instrument(app).expose(app)  # GET /metrics (request counts and latency)

    def analyse(img: np.ndarray) -> EmbedResponse:
        """Run detection and optional embedding on a decoded OpenCV BGR image.

        The shared backend is protected for the whole detect/embed sequence.
        Stage timings measure inference after acquiring the lock, so they omit
        time spent waiting for another request to finish.
        """
        b = state["backend"]
        with lock:
            t0 = time.perf_counter()
            # Faces narrower than MIN_FACE_PX are people outside the gate zone: drop them before the one-face
            # rule, so they neither cause MULTI_FACE nor get reported or embedded.
            faces = [f for f in b.detect(img) if f.bbox[2] - f.bbox[0] >= settings.min_face_px]
            t1 = time.perf_counter()
            # Data minimisation: only compute a template when there is exactly one face.
            # With 0 or 2+ faces the gate goes to manual anyway, so bystanders' templates are never computed.
            embedding = b.embed(img, faces[0]) if len(faces) == 1 else None
            t2 = time.perf_counter()
        out = [FaceOut(bbox=f.bbox, det_score=f.det_score) for f in faces]
        if embedding is not None:
            out[0].embedding = embedding.tolist()
        return EmbedResponse(model_id=b.model_id, dim=b.dim, faces=out,
                             timings_ms={"detect": round((t1 - t0) * 1000, 2), "embed": round((t2 - t1) * 1000, 2)})

    @app.post("/v1/embed", response_model=EmbedResponse, response_model_exclude_none=True)
    async def embed(request: Request) -> EmbedResponse:
        """Validate raw image bytes, decode them, and return face data as JSON.

        Missing embeddings are omitted by response_model_exclude_none. The total
        timer includes reading, decoding, and waiting for inference, but stops
        before FastAPI serializes and sends the response.
        """
        # Raw image bytes in the body (image/jpeg or image/png). Kept in memory only, never written or logged.
        t0 = time.perf_counter()
        data = await request.body()
        if not data:
            raise HTTPException(400, "empty body: send raw image bytes")
        # This checks the encoded byte size after reading the full body into memory.
        if len(data) > settings.max_image_bytes:
            raise HTTPException(413, f"image larger than {settings.max_image_bytes} bytes")
        img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            raise HTTPException(400, "body is not a decodable image")
        # Offload synchronous inference so it does not block the async event loop.
        # The lock in analyse still serializes access to this app's shared backend.
        result = await run_in_threadpool(analyse, img)
        result.timings_ms["total"] = round((time.perf_counter() - t0) * 1000, 2)
        return result

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        """Report the loaded backend's metadata; this does not run fresh inference."""
        b = state["backend"]
        return HealthResponse(status="ok", model_id=b.model_id, dim=b.dim, device=b.device, providers=b.providers)

    return app


# Uvicorn imports this object. Settings are read now; model loading waits for lifespan startup.
app = create_app(load_settings())
