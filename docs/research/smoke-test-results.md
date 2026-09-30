# Smoke-test results (local machine, 30 Sep 2026)

Run of `phase3-smoke-tests.md` steps 0–3, 10 and 11 on the POC development desktop. Step 9 (ONNX Runtime Java) was
skipped by decision: the chosen architecture runs inference in Python only. **These measured numbers override the
Phase 3 cloud reference numbers.** Raw data: `smoke/results.jsonl` (local, gitignored).

## Step 0: Machine

| Item | Value |
|---|---|
| GPU | NVIDIA GeForce RTX 4060, 8188 MiB VRAM |
| Driver / CUDA (driver-supported) | 591.86 / CUDA 13.1 |
| CPU | Intel Core i5-14400F, 10 cores / 16 threads |
| RAM | 15.8 GB |
| OS | Windows 11 Pro 10.0.26200 |
| Python | 3.11.9 (installed via winget for this POC; 3.12 and 3.14.2 also present) |
| Java | Oracle JDK 25.0.1 LTS (project compiles with `--release 21`; Boot 4.1.1 supports 17–26) |
| Maven | not installed system-wide; Maven wrapper 3.3.4 (Maven 3.9.16) from start.spring.io used instead |
| Docker | 29.7.2 (Docker Desktop installed; engine was not running at test time) |
| Node | 24.12.0 |

## Step 1: Python environment

- `insightface==2.0`, `opencv-python==5.0.0.93`, `numpy 2.4.6`.
- ONNX Runtime line: **`onnxruntime-gpu[cuda,cudnn]==1.30.0` (CUDA 13)**. `pip list` shows only `onnxruntime-gpu`.
- Available providers: `TensorrtExecutionProvider, CUDAExecutionProvider, CPUExecutionProvider`.
- **Pass.**

## Step 2: GPU really runs

- Session providers for `w600k_r50.onnx`: `['CUDAExecutionProvider', 'CPUExecutionProvider']` → `PASS GPU`.
- `buffalo_l` model pack (≈282 MB) downloaded to `%USERPROFILE%\.insightface\models\buffalo_l`.
- **Pass** (1.30.0 / CUDA 13 line works; CUDA 12 fallback not needed).

## Step 3: SCRFD-10G + ArcFace R50 (InsightFace 2.0, weights non-commercial)

Input: InsightFace's bundled `t1.jpg` (6 faces), smoke tests only. n=50 (pipeline n=20), warm-up 5.

| Block | Device | p50 (ms) | p95 (ms) | Cloud 2 vCPU p50 (ms), for reference |
|---|---|---|---|---|
| Full pipeline, 6 faces (SCRFD@640 + 6× R50) | GPU | 38.5 | 39.5 | – |
| Full pipeline, 6 faces | CPU (all threads) | 505.8 | 715.7 | 1099 |
| SCRFD-10G detection @640 | GPU | 11.7 | 15.5 | – |
| SCRFD-10G detection @640 | CPU | 47.7 | 50.5 | 130–135 |
| ArcFace R50, one face | GPU | 4.0 | 4.3 | – |
| ArcFace R50, one face | CPU | 46.8 | 54.8 | 138 |
| ArcFace R50, one face | CPU capped at 4 threads (edge proxy) | 62.4 | 63.7 | – |

- Peak RSS of the whole script: **1503 MB**.
- GPU memory in use (whole card, includes desktop): 1116 MB before GPU sessions → 2401 MB with detector + recogniser
  sessions loaded, i.e. roughly **1.3 GB** for the model process.
- Sanity: 6 faces found, max impostor cosine 0.214 → **Pass**.
- Caveat: these are component timings on one sample image, not the end-to-end gate latency. The README latency table
  comes from `tools/bench_latency.py` (N=200, warm-up 20) later.

## Step 10: Java gate service (Spring Boot 4.1.1 + Resilience4j 2.4.0)

Built with the **Maven wrapper** (`mvnw.cmd`, wrapper 3.3.4 → Maven 3.9.16) taken from a start.spring.io project
(Boot 4.1.1, Java 21); no system-wide Maven install. **Built with JDK 25, targets Java 21.**

- `mvnw -q -B -DskipTests package`: 39 s on first run (includes downloading Maven and dependencies); fat jar 26.2 MB.
- Startup: `Started GateApp in 2.891 seconds` (Java 25.0.1). Working set after 5 calls: **271 MB**.
- Calls 1–3: `MANUAL_CHECK` / `IllegalStateException`; calls 4–5: `MANUAL_CHECK` / `CallNotPermittedException`.
- `/actuator/circuitbreakers`: `matcher` `"state":"OPEN"`, failedCalls 3, notPermittedCalls 2.
- Prometheus: `resilience4j_circuitbreaker_state{name="matcher",state="open"} 1.0`;
  `http_server_requests_seconds_count{...uri="/gates/{id}/events"} 5`.
- **Pass.**

## Step 11: Python gate service (FastAPI + pybreaker + tenacity + Prometheus + structlog)

Versions: fastapi 0.142.1, uvicorn 0.54.0, pybreaker 1.4.1, tenacity 9.1.4, prometheus-fastapi-instrumentator 8.1.0,
prometheus_client 0.26.0, structlog 26.1.0.

- Calls 1–2: `MANUAL_CHECK` / `ConnectionError`, breaker `closed`.
- Calls 3–5: `MANUAL_CHECK` / `CircuitBreakerError`, breaker `open`.
- `gate_decisions_total{decision="MANUAL_CHECK",gate="A12"} 5.0`.
- **Pass.** The Day-1 checkpoint fallback (FastAPI gate) is viable on this machine.

## Failures / deviations

- Step 9 skipped (decision, not a failure).
- Step 10 used the Maven wrapper instead of a system Maven (decision, not a failure).
- CPU rows in step 3 use InsightFace's default thread count (all 16 threads). For the `cpu4` benchmark the
  model-service builds ONNX Runtime sessions itself with `intra_op_num_threads=4`, because InsightFace's `get_model`
  ignores `sess_options` (InsightFace issue #2899).
- The checklist's step 1 `pip install` line also lists `faiss-cpu`, `uniface`, `trackers` and `supervision`; they are
  only needed for steps 5–8, which are not in the pre-work set, so they were not installed.
- The checklist says to copy results to `phase3-smoke-results.md`; per CLAUDE.md they are saved here instead.
