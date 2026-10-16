# Facial Recognition at Airline Boarding Gates: Phase 3 Open-Source Building Blocks (as of 30 Sep 2026)

**Input:** Phase 2 handoff (shortlist A1–A9, authoritative), Phase 1 handoff (problem context), and both full reports.

**Method:** I visited every repository listed here before listing it, in two ways:
- **Git:** a shallow blobless clone gave the last commit date, the LICENSE text, README license lines and release-tag dates.
- **Web:** fetching each GitHub page gave stars, open issues and README details.

Package versions were checked on PyPI and Maven Central. Core blocks were then **run** in the cloud workspace: 2 vCPU, no GPU, Python 3.11, Java 21, on 30 Sep 2026. Latencies marked [M] come from that machine. They are CPU reference points, not your desktop's numbers. Your GPU model is still a placeholder in the project context, so every GPU number is left for the smoke-test checklist to collect.

**Labels:** [V] = VERIFIED from the repo, docs or registry (URL given). [M] = measured by me in the cloud workspace. [I] = INFERRED. ⚠️OLD = last commit or release older than about 24 months (before about Sep 2024).

## Corrections to earlier phases
- **Phase 2 was wrong about OFIQ's mobile support.** Phase 2 (citing Biometric Update, 23 Jul 2026) said a mobile OFIQ 1.1 was "planned". The OFIQ changelog shows v1.1.0 (2025-10-31) already "provides compilation scripts and demo apps for Android and iOS". v1.2.0 then added a C API, a Python wrapper and a web service; the changelog dates it 2026-03-03, and the tag commit is 2026-06-15. [V] https://github.com/BSI-OFIQ/OFIQ-Project (CHANGELOG.md). The changelog also says v1.1.x is *conformant* to ISO/IEC 29794-5, while the *reference* implementation is v1.0.3. [V]
- **Update to Phase 2 A1 (EdgeFace weight licence).** The Idiap model card licenses the EdgeFace weights under **CC BY-NC-SA 4.0**. UniFace's attribution page lists them as BSD-3-Clause. These conflict, so I follow the authors' card. [V] https://huggingface.co/Idiap/EdgeFace-Base; https://github.com/yakhyo/uniface (docs/license-attribution.md)

## TL;DR
- **Fastest credible stack (Python).** Use `insightface` 2.0 (released 9–10 Sep 2026) for SCRFD detection and ArcFace-R50 recognition. It now also ships an optional RGB liveness addon and a REST "InsightFace Server" (0.3.1). All InsightFace pretrained models are **non-commercial research only**, which is fine for a POC but worth stating up front. [V] https://github.com/deepinsight/insightface
- **License-clean stack that runs identically in Python and Java.** Use the OpenCV Zoo ONNX models: YuNet detection (MIT), SFace recognition (Apache-2.0) and eDifFIQA(T) quality (CC BY 4.0). They run on CPU at 36 / 11 / 9 ms [M], and SFace runs from Java ONNX Runtime at about 15 ms [M]. [V] https://github.com/opencv/opencv_zoo; https://huggingface.co/opencv
- **Glue libraries are mature in both ecosystems, and I ran both.** Spring Boot 4.1.1 + `resilience4j-spring-boot4` 2.4.0 + Actuator/Prometheus opened the breaker after 3 failures and fell back to `MANUAL_CHECK` [M]. FastAPI + pybreaker + tenacity + Prometheus did the same [M].
- **Search is not a bottleneck at flight scale.** Exact search over 500 × 512-d templates takes 0.04 ms with FAISS or NumPy, and 100k takes about 14 ms on CPU [M]. pgvector (PostgreSQL License) fits the user's PostgreSQL stack and has a Java client.
- **Weakest area: PAD and cancelable templates.** Open PAD models are small MiniFASNet classifiers, trained on undisclosed data (Silent-Face) or on non-commercial data (CelebA-Spoof). The public PolyProtect implementations are GPL-3.0 (Kotlin) or research-only licensed. [V]

## A. Repository tables

Column key:
- **★** = approximate stars.
- **Maint.** = maintenance status: A = active (commit in the last 3 months), S = slow (3–24 months), I = inactive (more than 24 months).
- **Weights** = where the pretrained weights come from.
- **GPU** = CUDA/TensorRT support.
- **ONNX/CPU** = ONNX export and CPU inference support.
- **Repro** = whether the paper's accuracy has been independently reproduced.

### A1. Detection and recognition

| Repo (URL) | Implements | Lang / framework | Code licence | Weights licence | Last commit / release | ★ | Maint. | Weights | GPU | ONNX / CPU | Install | Repro | Notable open issues |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| https://github.com/deepinsight/insightface | ArcFace (CVPR19), SCRFD (ICLR22): **official** | Python, ONNX Runtime; C++ SDK | MIT | **Non-commercial research only**; commercial via recognition-oss-pack@insightface.ai | 2026-09-09; PyPI `insightface` 2.0 | 29.8k | A | Auto-download: buffalo_l (SCRFD-10G + R50@WebFace600K, 326 MB), buffalo_s/sc, raccoon_s/l | CUDA via onnxruntime-gpu; TensorRT only via third parties | Native ONNX; CPU yes [M: SCRFD-10G@640 135 ms, R50 138 ms/face] | Easy (pip, then swap in onnxruntime-gpu) | Self-reported only (IJB-C E4 97.25 for buffalo_l; per-group MR-ALL: African 90.29, South Asian 93.16, East Asian 74.96) | 1.2k open. #2899 `get_model` drops `sess_options`, causing CPU thread oversubscription (Sep 2026). #2877 low SCRFD confidence (Apr 2026). Sets `ORT_DISABLE_TELEMETRY=1` [V] |
| https://github.com/mk-minchul/AdaFace | AdaFace (CVPR22): **official** | Python, PyTorch 1.8 / Lightning | MIT | No separate statement; trained on WebFace/MS1M, whose dataset terms are non-commercial [I] | 2024-06-10 ⚠️ | 960 | S (points to CVLFace) | Google Drive (R18–R100) | CUDA (PyTorch) | No official ONNX (third-party `yakhyo/adaface-onnx`); CPU via PyTorch | Medium | **Disputed**: #172 "TinyFace results are lower than mentioned" (Nov 2025), #169 IJB 1:N evaluation question | 97 open; input is BGR, not RGB (#165) |
| https://github.com/mk-minchul/CVLFace | AdaFace / KP-RPE ViT toolkit: **official** | Python, PyTorch | MIT | Hugging Face-hosted; same dataset caveat [I] | 2025-05-26 | 254 | S | Hugging Face | CUDA | No ONNX documented | Medium | Not verified | Not checked |
| https://github.com/otroshi/edgeface | EdgeFace (TBIOM 2024): **official** | Python, PyTorch | BSD-3 | **CC BY-NC-SA 4.0** (Idiap card) | 2025-08-07 | 180 | S | torch.hub / Hugging Face (1.24M–18.2M params) | CUDA | ONNX via `yakhyo/edgeface-onnx`; CPU [M: xxs 2.4 ms via UniFace] | Easy | Won compact track of IJCB 2023 EFaR [V repo] | Few |
| https://github.com/bytedance/LVFace | LVFace (ICCV25): **official** | Python, PyTorch + ONNX | MIT | **Non-commercial research only** | 2025-08-21 | 132 | S | ONNX + .pt on Hugging Face (T/S/B/L) | onnxruntime-gpu 1.19.2 pinned | ONNX yes; CPU heavy for B/L [I] | Easy–Med | MFR-Ongoing #1 academic track, Dec 2024 (server-evaluated) [V] | 2 open |
| https://github.com/opencv/opencv_zoo (YuNet, SFace, eDifFIQA) | YuNet (IJAC 2023), SFace (TIP 2022): ONNX ports by the authors or contributors | Python / C++ / Java via OpenCV DNN | Apache-2.0 | YuNet **MIT**; SFace **Apache-2.0**; eDifFIQA(T) **CC BY 4.0** | 2026-05-28 (adds YuNet `2026may` dynamic-shape model for OpenCV 5) | 1.1k | A | In repo (Git LFS) and official Hugging Face mirror `opencv/*` | OpenCV DNN CUDA only on 4.x; OpenCV 5 prints "Targets are not supported by the new graph engine" [M] | Native ONNX; CPU [M: YuNet 36 ms, SFace 11 ms, eDifFIQA 9 ms] | Easy | Zoo's own evaluation: WIDER Face 0.884/0.866/0.750, SFace LFW 99.40. The same YuNet README also quotes 0.834/0.824/0.708 (**conflict**) | 12 open |
| https://github.com/yakhyo/uniface | Toolkit: **unofficial** ONNX ports (RetinaFace, SCRFD, AdaFace, EdgeFace, MiniFASNet, eDifFIQA, BYTETracker, FAISS store) | Python, ONNX Runtime | MIT | Per model: SCRFD/ArcFace non-commercial; AdaFace "MIT"; EdgeFace "BSD-3" (conflicts with CC BY-NC-SA); MiniFASNet Apache-2.0 | v4.0.0 2026-08-14; commit 2026-09-21 | 1.7k | A | Auto-download, SHA-256 verified | `uniface[gpu]` | ONNX; CPU [M: RetinaFace-mnet 50 ms, AdaFace-IR18 20 ms, MiniFASNet 3 ms, eDifFIQA-T 10.5 ms] | Easy | Not independently verified | 3 open (feature requests) |

### A2. Quality assessment (P4)

| Repo | Implements | Lang / fw | Code lic. | Weights lic. | Last commit / release | ★ | Maint. | Weights | GPU | ONNX / CPU | Install | Repro | Issues |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| https://github.com/BSI-OFIQ/OFIQ-Project | ISO/IEC 29794-5 reference (v1.0.3) and conformant (v1.1+) implementation | C/C++, ONNX Runtime 1.18.1; Python wrapper and web service since 1.2.0 | MIT | Models from the ISO portal (422 MB zip): all MIT or Apache-2.0 derived. UQS = MagFace iResNet50 modified to output only the norm (Apache-2.0) [V, read from the zip] | v1.2.0 (tag 2026-06-15) | 125 | A | Downloaded during the build | Not documented | ONNX models; CPU; thread-safe since 1.1.0 | **Hard** (CMake ≥3.26 + Conan 2.18.1; `build.cmd` on Windows) | ISO conformance tests. DHS found limited utility of the UQS (Phase 2) | 3 open: #114–116, pitch-mapping clarifications (Jul 2026) |
| https://github.com/LSIbabnikz/eDifFIQA | eDifFIQA (TBIOM 2024): **official** | Python, PyTorch | MIT | MIT on repo (SharePoint / Hugging Face). OpenCV Zoo ONNX (T) is CC BY 4.0 | 2025-08-06 | 41 | S | T/S/M/L | CUDA | ONNX (T) via OpenCV Zoo; also T–L via `yakhyo/face-image-quality-assessment` (MIT) | Easy via ONNX | Repo: eDifFIQA(L) top for Kiosk-to-Entry in NIST FATE-Quality (claim) | — |
| https://github.com/fdbtrs/CR-FIQA | CR-FIQA (CVPR23): **official** | Python, PyTorch 1.7.1 | **CC BY-NC 4.0** | Google Drive (R100@MS1MV2, R50@CASIA): non-commercial | 2025-07-23 | 145 | S | Yes | CUDA | No ONNX | Medium | Repo: 1st/2nd in NIST FATE-Quality (claim) | 10 open |
| https://github.com/LSIbabnikz/FROQ | FROQ (IJCB 2025, training-free): **official** | Python, PyTorch | MIT | Needs AdaFace checkpoint + calibration set (Git LFS) | 2025-08-29 | 3 | S | Partial | CUDA | ~11 ms/image (repo claim) | Medium | Not verified | — |
| https://github.com/oufuzhao/CLIB-FIQA | CLIB-FIQA (CVPR24): **official** | Python, PyTorch + CLIP + dlib | MIT | Google Drive | 2025-10-18 | 42 | S | Yes | CUDA | No ONNX | Med–Hard | Not verified | — |
| https://github.com/IrvingMeng/MagFace | MagFace (CVPR21): **official** | Python, PyTorch | Apache-2.0 | Google Drive / Baidu (MS1MV2-trained) | 2023-12-19 ⚠️ | 695 | I | Yes | CUDA | ONNX only via OFIQ's UQS model | Medium | Used inside OFIQ | 17 open |

### A3. Presentation attack detection (P6)

| Repo | Implements | Lang / fw | Code lic. | Weights lic. | Last commit / release | ★ | Maint. | Weights | GPU | ONNX / CPU | Install | Repro | Issues |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| https://github.com/minivision-ai/Silent-Face-Anti-Spoofing | MiniFASNet: **official** (company) | Python, PyTorch | Apache-2.0 | Included (V1SE/V2); **training data not released** | 2020-08-05 ⚠️ | 1.8k | I | Yes | CUDA | Community ONNX only (#136, #138) | Medium | Claims 97.8% TPR @1e-5 FPR on private data: **not reproducible** | #135 "Anyone maintaining this project?" (Jun 2025) |
| https://github.com/yakhyo/face-anti-spoofing | MiniFASNet port: **unofficial** | Python, PyTorch/ONNX | Apache-2.0 | Apache-2.0 | 2026-03-25 | 33 | S | .pth + .onnx | ORT | ONNX; CPU [M: 3 ms via UniFace] | Easy | No metrics published | — |
| https://github.com/facenox/face-antispoof-onnx | MiniFASNetV2-SE retrained: **unofficial** | Python, ONNX | Apache-2.0 | Apache-2.0 as stated, but trained on **CelebA-Spoof (non-commercial only)** [V https://github.com/ZhangYuanhan-AI/CelebA-Spoof] | v1.0.0 2026-01-03; commit 2026-08-26 | 271 | A | In repo (INT8 600 KB) | ORT GPU | ONNX; CPU [M: 8.7 ms INT8] | Easy | 98.20% acc on CelebA-Spoof test (self-reported) | LIMITATIONS: not trained on 3D masks; needs ≥64 px faces and even lighting |
| deepinsight/insightface liveness addon (https://github.com/deepinsight/insightface-model-addons) | RGB liveness: **official** InsightFace | Python, ONNX | MIT (library) | **Not stated**; the package says its pretrained models are non-commercial, so treat as non-commercial [I] | Released 2026-09-05 | — | A | 1.48 MB ONNX, SHA-256 verified | ORT GPU | CPU [M: +~70 ms per frame for 6 faces] | Easy (`addons=["liveness"]`) | Not verified | Release notes not readable (page did not render) |
| https://github.com/ZitongYu/CDCN | CDCN/CDCN++ (CVPR20): **official** | Python, PyTorch | **"Research purpose only; commercial use not allowed"** | No weights | 2021-05-01 ⚠️ | 613 | I | No | CUDA | — | Medium | Paper protocols only | 11 open |

### A4. Tracking and search (P2, P16, P1)

| Repo | Implements | Lang / fw | Code lic. | Weights lic. | Last commit / release | ★ | Maint. | Weights | GPU | ONNX / CPU | Install | Repro | Issues |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| https://github.com/FoundationVision/ByteTrack | ByteTrack (ECCV22): **official** | Python, PyTorch + YOLOX | MIT | YOLOX person detectors (MOT17/20), not faces | 2022-12-11 ⚠️ | 6.7k | I | Yes | CUDA; TensorRT, DeepStream | ONNX / ncnn deploy | Medium | MOT Challenge test server (independent) [V] | 302 open |
| https://github.com/roboflow/trackers | ByteTrack, SORT, OC-SORT, BoT-SORT, C-BIoU: **clean-room re-implementations** | Python | Apache-2.0 | n/a (detector-agnostic) | v2.6.1 2026-09-24 | 3.6k | A | n/a | n/a | CPU [M: 0.6 ms/update; 6 faces kept 6 IDs over 60 frames] | Easy | — | — |
| https://github.com/mk-minchul/caface | CAFace (NeurIPS22): **official** | Python, PyTorch 1.10 | MIT | Google Drive (AdaFace WebFace4M-based) | 2023-01-11 ⚠️ | 47 | I | Yes | CUDA | No ONNX | Medium | Not verified | 5 open |
| https://github.com/facebookresearch/faiss | Faiss library: **official** | C++ / Python | MIT | n/a | v1.15.1 2026-09-16 | 40.9k | A | n/a | CUDA / ROCm / cuVS (conda). PyPI `faiss-gpu-cu12` is at 1.14.1.post1 | CPU [M: FlatIP 500 → 0.04 ms, 100k → 14 ms] | Easy (`faiss-cpu`) | n/a | 217 open; no Java binding documented |
| https://github.com/pgvector/pgvector | Vector search in PostgreSQL (HNSW, IVFFlat, exact) | C extension | PostgreSQL License | n/a | v0.8.6 2026-07-29 | 23.2k | A | n/a | n/a | CPU | Easy via Docker `pgvector/pgvector`; Windows build needs nmake | n/a | Java `com.pgvector:pgvector` 0.1.6 and Python `pgvector` 0.5.0 on the registries [V] |

### A5. Template protection, fairness and lifecycle (P10, P19, P7, P15)

| Repo | Implements | Lang | Code lic. | Weights | Last commit / release | ★ | Maint. | Notes |
|---|---|---|---|---|---|---|---|---|
| https://github.com/OpenMined/TenSEAL | CKKS/BFV HE (on Microsoft SEAL) | Python / C++ | Apache-2.0 | n/a | v0.3.18 2026-09-12 | 1.0k | A | pip wheels for Windows x64 and Linux, Python ≥3.11 [V]. [M] Encrypted probe × 500-template gallery took 2.65 s (naive `matmul`), so IDFace-style packing is needed for real time [I] |
| https://github.com/openfheorg/openfhe-python | OpenFHE bindings (CKKS, BFV, BGV, TFHE) | Python / C++ | BSD-2 | n/a | v1.5.1.0 2026-04-10 | 160 | A | pip wheels only for Ubuntu 22.04/24.04. Windows needs WSL2 or a source build [V] |
| https://github.com/Simprints/Biometrics-SimPolyProtect | PolyProtect (IJCB 2025 deployment) | Kotlin / Android | **GPL-3.0** | n/a | 2025-08-30 | 2 | S | Copyleft and Android-only. The algorithm is small enough to re-implement from the paper [I] |
| https://github.com/idiap/BTPBench | Benchmark for PolyProtect and BioHashing (unlinkability, irreversibility) | Python 3.12 | **Research-only, non-commercial** | iResNet non-commercial; EdgeFace CC BY-NC-SA | 2026-09-29 | 0 | A | Evaluation reference only |
| https://github.com/prevso1088/open-set-biometrics | Open-set loss (ECCV24): **official** | Python (OpenGait) | **No licence file** | none | 2024-10-02 | 10 | S | Gait-focused README; no face example |
| https://github.com/YantaoShen/openBCT | BCT (CVPR20): first author's "non-official reimplementation" | Python, PyTorch | BSD-2 | none | 2021-07-12 ⚠️ | 55 | I | ImageNet/Places only; "does NOT provide" face code |
| https://github.com/manuelaguadomtz/pyeer | EER, FMR100/1000, DET, CMC metrics | Python | MIT | n/a | v0.5.5 2021-06-12 ⚠️; commit 2023-12-16 | 64 | I | Small and stable. Fine for ISO/IEC 19795-style reporting in a POC [I] |
| https://github.com/visionjo/facerec-bias-bfw | BFW balanced faces (4 ethnicities × 2 genders) | Python | BSD-3 (code) | Data "released for research purposes" | 2026-06-09 | 52 | S | Use only for evaluation. Dataset terms need checking before any non-research use [I] |

FairCal: no official code repository could be verified (the author's GitHub repo list is blocked by robots.txt), so it is not listed.

### A6. End-to-end frameworks with REST APIs

| Repo | What | Lang | Code lic. | Weights lic. | Last commit / release | ★ | Maint. | GPU / CPU | Notes |
|---|---|---|---|---|---|---|---|---|---|
| InsightFace Server (https://github.com/deepinsight/insightface/tree/master/server) | Self-hosted REST (31 `/v1` ops), Web UI, SQLite, exact 1:N, optional liveness, "model-bound Collections" | Python | MIT | **Non-commercial** (a `MODEL.LICENSE` ships per model) | 0.3.1; server dir changed 2026-09-09 | (29.8k repo) | A | Docker `ghcr.io/deepinsight/insightface-server:0.3.1-cpu` / `-cuda12`; Linux x86_64 | Closest open analogue of a "match service". Model-bound collections are a good talking point for P15 [I] |
| https://github.com/exadel-inc/CompreFace | **Spring Boot 2.5.13 (Java 17) API + Python Flask embedding calculator + PostgreSQL** | Java + Python | Apache-2.0 | FaceNet / InsightFace models (InsightFace ones non-commercial) | v1.2.0 2023-08-17; commit 2023-11-14 | 8.1k | S (stale) | docker-compose CPU/GPU | Open-source precedent for option (2), a Java API with a Python model service [V] |
| https://github.com/serengil/deepface | Wrapper for 10 recognizers, 15+ detectors, `anti_spoofing=True`, DB search (incl. pgvector) | Python (TF or PyTorch) | MIT | Mixed per model | v0.0.101 2026-09-14 | 23.5k | A | REST via gunicorn/Flask | Broad but heavy. Default model is VGG-Face |
| https://github.com/SthPhoenix/InsightFace-REST | FastAPI + TensorRT/ORT serving InsightFace models | Python | Apache-2.0 | Non-commercial (InsightFace) | Release v0.7.0.0 2021-11-06 ⚠️; commit 2025-06-01 | 624 | S | TensorRT GPU / ORT CPU | Superseded by the official InsightFace Server [I] |
| https://github.com/ageitgey/face_recognition | dlib wrapper | Python | MIT | dlib models | Tag v1.2.2 (old); docs commit 2026-06-25 | 56.7k | I | CPU (dlib) | Only an LFW 99.38% claim; "Windows isn't officially supported" [V] |
| https://github.com/HyperInspire/InspireFace | C/C++ SDK (detection, recognition, liveness, quality); Python and Android JNI bindings | C++ | Not stated in README | **Academic only**; commercial needs a licence [V] | v1.2.4 2026-09-29 | 271 | A | TensorRT 10, CPU, NPUs | The InsightFace company's SDK |

### A7. Java inference options

| Repo | What | Code lic. | Latest (Maven) | ★ | Maint. | GPU | Evidence |
|---|---|---|---|---|---|---|---|
| https://github.com/microsoft/onnxruntime (Java API) | Runs any ONNX model from the JVM | MIT | `com.microsoft.onnxruntime:onnxruntime` / `onnxruntime_gpu` 1.30.0 (tag 2026-09-09) [V Maven] | 21.9k | A | CUDA/TensorRT on Windows x64 and Linux x64 [V docs] | [M] SFace p50 14.7 ms, ArcFace-R50 146 ms (CPU), peak RSS 252 MB. Java 8+ |
| https://github.com/deepjavalibrary/djl | Engine-agnostic DL for Java (PyTorch, OnnxRuntime, TensorFlow, MXNet engines; OpenCV extension) | Apache-2.0 | `ai.djl:api` 0.38.0 (tag 2026-09-09; README release notes stop at 0.36.0) [V] | 4.8k | A | CUDA through engines | Face example uses facenet-pytorch TorchScript; no detector in that example [V docs] |
| https://github.com/opencv/opencv (Java bindings) | OpenCV 5.0.0 (2026-06-05), 4.14.0 (2026-07-17) | Apache-2.0 | No official Maven artifact found [I] | 90.9k | A | — | Java bindings come from source builds or third-party packages |
| https://github.com/openpnp/opencv | Prebuilt OpenCV Java jar with natives | OpenCV licence text | `org.openpnp:opencv` 4.9.0-0 (2023-12-28) ⚠️ | 371 | S | CPU | Lags OpenCV by about 2 years |
| https://github.com/bytedeco/javacv | JavaCPP presets (OpenCV, FFmpeg) | Apache-2.0 **or** GPLv2 + Classpath exception | `javacv-platform` 1.5.14 (2026-08-10); OpenCV preset 4.14.0-1.5.14 [V Maven] | 8.3k | A | `opencv-platform-gpu` artifact | Large native download |

### A8. Engineering libraries: Python

| Repo | Pattern | Licence | Latest | ★ | Maint. | Notes |
|---|---|---|---|---|---|---|
| https://github.com/fastapi/fastapi | REST service | MIT | 0.142.0 (2026-09-29) | 102.7k | A | [M] used in gate smoke test |
| https://github.com/jd/tenacity | Retries with backoff and jitter | Apache-2.0 | PyPI 9.1.4 (git tag 9.2.0 2026-08-05) | 8.7k | A | asyncio, Trio, Tornado |
| https://github.com/hynek/stamina | Opinionated tenacity wrapper with Prometheus and structlog hooks | MIT | 26.1.0 (2026-04-13) | 1.5k | A | Safer defaults |
| https://github.com/danielfm/pybreaker | Circuit breaker | BSD-3 | 1.4.1 (2025-09-21); commit 2026-09-25 | 691 | A | Async only via Tornado; Redis-backed shared state [V]. [M] opened after 3 failures |
| https://github.com/prometheus/client_python | Metrics | Apache-2.0 | 0.26.0 (2026-07-24) | 4.4k | A | Custom counters/histograms |
| https://github.com/trallnag/prometheus-fastapi-instrumentator | `/metrics` with HTTP RED metrics | ISC | 8.1.0 (2026-07) | 1.5k | A | One line: `Instrumentator().instrument(app).expose(app)` |
| https://github.com/open-telemetry/opentelemetry-python | Traces, metrics, logs | Apache-2.0 | 1.45.0 (2026-09-25) | 2.6k | A | Traces and metrics stable, logs "development". FastAPI instrumentation 0.66b0 is **Beta** [V] |
| https://github.com/hynek/structlog | Structured JSON logging | MIT / Apache-2.0 | 26.1.0 (2026-06-06) | 5.0k | A | contextvars support |

### A9. Engineering libraries: Java (Spring Boot-idiomatic unless noted)

| Repo | Pattern | Licence | Latest | ★ | Maint. | Spring fit |
|---|---|---|---|---|---|---|
| https://github.com/resilience4j/resilience4j | CircuitBreaker, Retry, Bulkhead, TimeLimiter, RateLimiter | Apache-2.0 | 2.4.0 (2026-03-14); `resilience4j-spring-boot4` 2.4.0 on Maven [V]. README: "Resilience4j 3 requires Java 21" | 10.8k | A | Idiomatic. [M] `@CircuitBreaker` + fallback + `/actuator/circuitbreakers` + Prometheus gauges |
| https://github.com/spring-projects/spring-boot (Actuator) | Health, metrics, Prometheus endpoints; Boot 4 `spring-boot-starter-opentelemetry` | Apache-2.0 | 4.1.1 (2026-08-11); Java 17–26 [V] | 81.4k | A | Idiomatic. The Spring team's favoured OTel option [V https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/] |
| Spring Framework 7 core resilience (https://docs.spring.io/spring-framework/reference/core/resilience.html) | `@Retryable`, `@ConcurrencyLimit`, `RetryTemplate` | Apache-2.0 | 7.0.x | — | A | Built in, but **no circuit breaker** [V] |
| https://github.com/spring-cloud/spring-cloud-circuitbreaker | Abstraction over Resilience4j / Spring Retry | Apache-2.0 | 5.0.3 (2026-08-03) | 358 | A | Optional indirection |
| https://github.com/micrometer-metrics/micrometer | Metrics facade (Prometheus, OTLP) | Apache-2.0 | 1.17.1 (2026-08-13) | 4.9k | A | Actuator's metrics layer |
| https://github.com/micrometer-metrics/tracing | Tracing facade (OTel / Brave bridges) | Apache-2.0 | 1.7.1 | 300 | A | Used by Boot's tracing |
| https://github.com/open-telemetry/opentelemetry-java-instrumentation | Zero-code `-javaagent` | Apache-2.0 | 2.31.1 (2026-08-23) | 2.6k | A | Alternative to the Boot starter; possible version-mismatch pitfalls [V Spring blog] |
| https://github.com/spring-projects/spring-modulith | Event Publication Registry (outbox-like, same-transaction log with republish); Kafka/AMQP/JMS externalization; outbox mode via Namastack/JobRunr since 2.1 | Apache-2.0 | 2.1.1 (2026-08-25) | 1.2k | A | Idiomatic [V docs] |
| https://github.com/namastack/namastack-outbox | Transactional outbox for Spring Boot (JDBC, JPA, Mongo) | Apache-2.0 | 1.9.0 | 377 | A | Integrates with Spring Modulith |
| https://github.com/debezium/debezium | CDC plus Outbox Event Router SMT | Apache-2.0 | 3.7.0.Final (2026-09-29) | 13.1k | A | Needs Kafka Connect or Debezium Server; too heavy for 2 days [I] |

## B. Viability for a 2-day POC on a single-GPU desktop

| Repo / block | Score (1–5) | Reason | Acceptable on CPU? |
|---|---|---|---|
| insightface 2.0 (SCRFD + ArcFace, liveness addon) | **5** | One pip install, auto-downloaded ONNX models, CUDA via ORT, liveness gating built in | Yes. About 1.1 s/frame for 6 faces on 2 vCPU [M]; a desktop CPU and fewer faces will be much faster [I] |
| OpenCV Zoo YuNet + SFace + eDifFIQA(T) | **5** | Tiny, permissive, same ONNX files usable from Python, OpenCV Java and ORT Java | Yes (36 + 11 + 9 ms) [M] |
| UniFace 4.0 | **5** | One API for detection, AdaFace/EdgeFace, MiniFASNet, eDifFIQA, BYTETracker, FAISS store | Yes (all under 50 ms) [M] |
| ONNX Runtime Java 1.30.0 | **4** | Runs the same ONNX models from Spring Boot [M]. Pre/post-processing (SCRFD decoding, alignment) must be hand-written in Java [I] | Yes |
| roboflow/trackers (ByteTrack) | **5** | pip, detector-agnostic, IDs stable in the synthetic walk-through [M] | Yes |
| FAISS / NumPy exact search | **5** | Trivial at flight scale [M] | Yes |
| pgvector | **4** | Fits the PostgreSQL/Hibernate world; Docker one-liner. [M] `pgvector/pgvector:pg17` (extension 0.8.6): exact top-2 over 500 rows in 5.3 ms | Yes |
| Resilience4j + Actuator + Micrometer (Boot 4.1.1) | **5** | Built and run end to end [M]; directly matches the target stack | Yes |
| FastAPI + pybreaker + tenacity + Prometheus + structlog | **5** | Built and run end to end [M] | Yes |
| facenox MiniFAS INT8 | **4** | Drop-in ONNX (Python or Java) | Yes (8.7 ms) [M] |
| TenSEAL | **3** | Works on Windows, but naive encrypted 1:N is seconds, not milliseconds [M] | Yes (slow) |
| LVFace | **3** | Strongest academic model and ships ONNX, but heavy and non-commercial | B/L too slow for edge [I] |
| DJL | **3** | Mature but adds an abstraction layer. The face example lacks a detector | Yes |
| OFIQ 1.2.0 | **2** | Standards-exact, but a C++/Conan build plus a 422 MB model download. Use eDifFIQA for the POC and cite OFIQ [I] | Yes (CPU-only) |
| CR-FIQA / CLIB-FIQA / FROQ | **2–3** | PyTorch-only, Google Drive or LFS weights; CR-FIQA is non-commercial | Yes (R50) |
| DeepFace | **3** | Broad, but TF/PyTorch-heavy with mixed licences | Yes |
| InsightFace Server (Docker) | **3** | Instant REST demo on Linux/WSL2, but it hides the logic the POC should show | Yes (CPU image) |
| CompreFace | **2** | Right architecture (Java + Python) but stale since 2023, on Spring Boot 2.5 | Yes |
| AdaFace / CVLFace / EdgeFace official repos | **3** | Better consumed as ONNX via UniFace | Yes |
| CAFace | **2** | Old PyTorch and Google Drive weights; use quality-weighted averaging instead [I] | Yes |
| CDCN, Silent-Face original, openBCT, open-set-biometrics, SimPolyProtect, BTPBench | **1** | Licence, maintenance or scope blockers (see Rejected) | — |

## C. License risk notes
1. **Non-commercial weights run through most "SOTA-looking" models.**
   - InsightFace packs (SCRFD, ArcFace, raccoon) and, by the package's statement, its liveness addon are non-commercial. [V]
   - LVFace weights are non-commercial. [V]
   - EdgeFace weights are CC BY-NC-SA 4.0. [V]
   - CR-FIQA is CC BY-NC 4.0 for code **and** weights. [V]
   - CDCN code is research-only. [V]
   - InspireFace models are academic-only. [V]
   - BTPBench code is research-only. [V]

   All of these are acceptable for a personal prototype. [I] State it explicitly: production would use licensed SDKs, as Phase 2 found.
2. **Training-data provenance is separate from the weights licence.** UniFace warns that WebFace260M (AdaFace, EdgeFace), WIDER FACE (most detectors) and MS-Celeb-1M carry non-commercial dataset terms, even where the weights are released under MIT/BSD. [V yakhyo/uniface docs/license-attribution.md]
3. **Hidden data restrictions in PAD models.**
   - facenox's weights are Apache-2.0, but it was trained on CelebA-Spoof, which is "available for non-commercial research purposes only". [V]
   - Silent-Face's training data is undisclosed. [V]
4. **Copyleft and unusual licences.**
   - BoxMOT is AGPL-3.0, so rejected. [V]
   - SimPolyProtect is GPL-3.0. [V]
   - JavaCV is dual-licensed Apache-2.0 / GPLv2+CE, so choose Apache. [V]
   - asgi-idempotency-header is BSD-4-Clause, which has an advertising clause. [V]
5. **No licence means it cannot be used.** This applies to open-set-biometrics and face-recognition-java. [V]
6. **A license-clean path exists.** [V]
   - Models: YuNet (MIT), SFace (Apache-2.0), eDifFIQA(T) (CC BY 4.0, attribution required), OFIQ and its models (MIT/Apache-derived), MiniFASNet via Silent-Face/yakhyo (Apache-2.0, data provenance unknown).
   - Libraries: trackers (Apache-2.0), FAISS (MIT), pgvector (PostgreSQL License), ONNX Runtime (MIT), all Spring and Micrometer libraries (Apache-2.0).
7. **Telemetry in a security context.** ONNX Runtime has a telemetry uploader on non-Windows platforms, and InsightFace force-sets `ORT_DISABLE_TELEMETRY=1`. [V python-package/docs/runtime.md] Set it yourself when not using InsightFace. [I]
8. **Biometric sample data.** Use InsightFace's bundled `t1.jpg` only for smoke tests. For the recorded demo, use consenting people (yourself) or synthetic faces. [I]

## D. Recommended building blocks (primary and fallback)

| Component | Primary (language) | Fallback (language) | Why |
|---|---|---|---|
| Detection | **SCRFD-10G** from `insightface` 2.0 buffalo_l (Python; the ONNX also loads in Java ORT, but anchor/NMS decoding must be ported) | **YuNet 2023mar** from OpenCV Zoo (Python **and** Java via `FaceDetectorYN`; MIT) | SCRFD is more accurate and multi-face capable. YuNet is licence-clean, has built-in Java decoding and takes 36 ms on CPU [M] |
| Recognition | **ArcFace R50 @ WebFace600K** (`w600k_r50.onnx`, Python or Java ORT; non-commercial) | **SFace** (OpenCV Zoo; Python and Java; Apache-2.0). For edge demos: **EdgeFace-xxs** via UniFace (non-commercial) | R50 IJB-C E4 97.25 vs SFace LFW 99.40 (weaker model), both self-reported [V]. SFace is 11 ms CPU and license-clean [M] |
| Quality (P4) | **eDifFIQA(T)** ONNX (OpenCV Zoo, CC BY 4.0; Python or Java) | **OFIQ 1.2.0** Python wrapper (C++; MIT; ISO/IEC 29794-5 conformant) | eDifFIQA separated sharp from blurred (0.707 vs 0.210) at 9 ms [M]. OFIQ gives standards-exact component feedback but is a hard build |
| PAD (P6) | **MiniFASNet V2** via UniFace (Python; ONNX portable to Java; Apache-2.0) | **InsightFace liveness addon** (Python; licence unstated, treat as non-commercial) **or** **facenox INT8 ONNX** (Python or Java; CelebA-Spoof data caveat) | All are passive RGB, 3–9 ms [M]. None has independent certification; production would need iBeta-tested PAD plus NIR/3D hardware [I] |
| Tracking (P16) | **roboflow/trackers ByteTrackTracker** (Python; Apache-2.0) | **UniFace BYTETracker** (Python; MIT) | No maintained Java tracker was found, so a Java port would be custom work [I] |
| Search (P1) | **Exact inner product**: NumPy or FAISS `IndexFlatIP` (Python); a plain float loop in Java [I] | **pgvector 0.8.6** (SQL from Python or Java; PostgreSQL License) | 500 × 512 in 0.04 ms [M]. pgvector persists galleries and supports manifest-scoped `WHERE flight_id = ?` queries [I] |
| Resilience (P14) | Java: **Resilience4j 2.4.0 + `resilience4j-spring-boot4`**. Python: **pybreaker 1.4.1 + tenacity 9.1.4** | Java: **Spring Framework 7 `@Retryable`/`@ConcurrencyLimit`** (no breaker) or Spring Cloud CircuitBreaker. Python: **stamina** (retries) | All four combinations validated or documented [M/V] |
| Observability (P15) | Java: **Actuator + Micrometer Prometheus + `spring-boot-starter-opentelemetry`**. Python: **prometheus-fastapi-instrumentator + prometheus_client + structlog** | Java: **OTel Java agent 2.31.1**. Python: **OpenTelemetry Python 1.45 + FastAPI instrumentation (Beta)** | Custom metrics: `gate_decisions_total{decision}`, match-score histogram, top-2 margin histogram, fallback rate [I] |
| Integration (P13) | Java: **Spring Modulith 2.1.1 Event Publication Registry** (JPA/JDBC) | Java: **Namastack Outbox 1.9.0**. Python: SQLAlchemy outbox table plus a DB unique key on `Idempotency-Key` [I] | No maintained Python outbox or idempotency library was found (asgi-idempotency-header last released 2022) [V] |
| Template protection (P10/P19) | **TenSEAL 0.3.18** CKKS (Python; Apache-2.0) | **PolyProtect re-implemented from the paper** (Python or Java, about 50 lines) [I] | TenSEAL demonstrates encrypted scoring, but naive 1:N took 2.65 s for 500 templates [M], which is itself a talking point |
| Evaluation (P7) | **pyeer** (MIT) + BFW (research data) | Hand-rolled FNMR/FMR per group (NumPy) | Enough to show ISO/IEC 19795-10-style differentials [I] |

## E. Language comparison (inputs for Phase 4, no decision)

| Criterion | (1) All Python | (2) Java/Spring Boot gate service + Python model service | (3) All Java |
|---|---|---|---|
| Model availability | Everything in A1–A4 is pip-installable. InsightFace, UniFace and OFIQ (Python wrapper) are all tested or documented [M/V] | Same as (1) for models. The Java side needs none | Any ONNX model via ORT Java [M]. OpenCV Java covers YuNet/SFace natively. SCRFD decoding, alignment, ByteTrack and the MiniFASNet crop logic must be ported [I]. InspireFace's JNI is Android-oriented [V] |
| Inference maturity | ORT Python, OpenCV 5.0, InsightFace 2.0 (Sep 2026) [V] | Python inference plus a mature Spring stack [V] | ORT Java 1.30.0 on Maven, same release as Python [V]. DJL 0.38.0 active [V]. Prebuilt OpenCV Java lags (openpnp 4.9.0, Dec 2023; JavaCV 4.14.0) [V] |
| GPU support | `onnxruntime-gpu` 1.30.0 wheels require **CUDA 13** (`nvidia-cuda-runtime~=13.0`, `nvidia-cudnn-cu13`); 1.26.0 is the CUDA 12 line [V wheel metadata]. ORT's install page still says CUDA 12 is the default (**docs conflict**) | GPU stays in Python; the Java side is CPU-only | `onnxruntime_gpu` 1.30.0 exists for Windows/Linux x64 [V]. Which CUDA major it targets is not documented, so the smoke test checks it [I]. OpenCV 5's new DNN engine ignores CUDA targets [M] |
| 2-day build speed | Fastest: an end-to-end pipeline and gate service ran here in minutes [M]. Matches the user's main stack (Python/FastAPI/PostgreSQL) [V profile] | Medium: two services, a REST/gRPC contract and two builds. Each half was validated separately [M] | Slowest: the Java model glue is custom and the Java/Spring experience is limited (project context) |
| Fit with the Java/Spring target stack | Weak on Java/Spring, but strong on the ML reasoning a reviewer may probe [I] | Strong: Spring Boot microservice, REST, Resilience4j, Actuator/Micrometer, JPA/Hibernate outbox, on-call metrics. The ML stays where the ecosystem is. The same split exists in open source (CompreFace) [V] | Strongest on paper, but the riskiest to finish, and it spends time on ML plumbing rather than system design [I] |
| Resilience / observability libraries | pybreaker (async only via Tornado), tenacity, Prometheus, structlog, OTel (FastAPI Beta) [V] | Resilience4j + Actuator on the gate side; minimal Prometheus on the model side | Resilience4j, Actuator, Micrometer, Boot 4 OTel starter, Spring Modulith [V] |
| Demo / explainability | One process, simplest recording | A clean diagram: gate service, identity-provider stub, model service. Circuit breaker and fallback are easy to show [I] | Single JVM; ML internals hidden in custom code |

## Rejected (details in the handoff)
BoxMOT (AGPL), CompreFace (stale, Spring Boot 2.5), face_recognition (dlib, no Windows support), InsightFace-REST (superseded), CDCN (research-only, no weights), Silent-Face original (unmaintained; use the ports), openBCT and open-set-biometrics (no face code or no licence), SimPolyProtect (GPL, Android), BTPBench (research-only), InspireFace (academic-only models), face-recognition-java (no licence, inactive), purgatory and aiobreaker (low adoption or stale), asgi-idempotency-header (stale, BSD-4-Clause).

## F. Handoff
Also saved separately as `docs/research/phase3-handoff.md`.

The smoke-test checklist is saved as `docs/research/phase3-smoke-tests.md`.

<handoff phase="3">
building_blocks:
  - component: detection (primary)
    language: Python (ONNX also loadable from Java ONNX Runtime; SCRFD decoding would need porting)
    repo_url: https://github.com/deepinsight/insightface
    model: SCRFD-10G (det_10g.onnx, buffalo_l pack), insightface 2.0 on PyPI
    license_code: MIT
    license_weights: Non-commercial research only (commercial via recognition-oss-pack@insightface.ai)
    runtime: both
    notes: Auto-downloads models; CUDA via onnxruntime-gpu. Measured CPU 135 ms at 640 px on 2 vCPU. Issue #2899 (get_model ignores sess_options). Sets ORT_DISABLE_TELEMETRY=1.
  - component: detection (fallback)
    language: both
    repo_url: https://github.com/opencv/opencv_zoo
    model: YuNet face_detection_yunet_2023mar.onnx (2026may dynamic-shape variant for OpenCV 5 ORT engine), also on HF opencv/face_detection_yunet
    license_code: Apache-2.0
    license_weights: MIT
    runtime: CPU
    notes: Built-in decoding via cv.FaceDetectorYN (Python and OpenCV Java). Measured 36 ms CPU with OpenCV 5.0.0. OpenCV 5's new engine ignores CUDA targets. README quotes two different WIDER Face APs.
  - component: recognition (primary)
    language: both
    repo_url: https://github.com/deepinsight/insightface
    model: ArcFace ResNet50 @ WebFace600K (w600k_r50.onnx, 512-d)
    license_code: MIT
    license_weights: Non-commercial research only
    runtime: both
    notes: Self-reported IJB-C E4 97.25; per-group MR-ALL (African 90.29, South Asian 93.16, East Asian 74.96). Measured CPU 138 ms/face (Python ORT) and 146 ms (Java ORT 1.30.0). GPU numbers are pending the smoke test.
  - component: recognition (fallback)
    language: both
    repo_url: https://github.com/opencv/opencv_zoo
    model: SFace face_recognition_sface_2021dec.onnx (MobileFaceNet, 128-d); also on HF opencv/face_recognition_sface
    license_code: Apache-2.0
    license_weights: Apache-2.0
    runtime: CPU (GPU via ORT possible)
    notes: License-clean. LFW 99.40 (zoo evaluation), so weaker than R50. Measured 11 ms (OpenCV DNN) and 14.7 ms (Java ORT). Edge alternative is EdgeFace-xxs via UniFace (2.4 ms, but weights CC BY-NC-SA 4.0).
  - component: quality assessment (primary)
    language: both
    repo_url: https://github.com/opencv/opencv_zoo
    model: eDifFIQA(T) ediffiqa_tiny_jun2024.onnx (HF opencv/face_image_quality_assessment_ediffiqa); original repo https://github.com/LSIbabnikz/eDifFIQA
    license_code: Apache-2.0 (zoo) / MIT (original)
    license_weights: CC BY 4.0 (attribution required)
    runtime: both
    notes: Measured 9 ms CPU. Sharp vs blurred score 0.707 vs 0.210. Use it for capture feedback and best-shot selection, not hard rejection (Phase 2).
  - component: quality assessment (fallback)
    language: Python (C++ core)
    repo_url: https://github.com/BSI-OFIQ/OFIQ-Project
    model: OFIQ 1.2.0 (ISO/IEC 29794-5 conformant; reference is v1.0.3); UQS = modified MagFace iResNet50 norm ONNX
    license_code: MIT
    license_weights: MIT / Apache-2.0 derived (per-model LICENSE.md in the ISO model zip)
    runtime: CPU
    notes: Hard build (CMake 3.26 + Conan 2.18.1; build.cmd on Windows; 422 MB model zip). Python wrapper and web service since 1.2.0. Cite as the standards path.
  - component: presentation attack detection (primary)
    language: Python (ONNX portable to Java)
    repo_url: https://github.com/yakhyo/uniface
    model: MiniFASNet V2 (minifasnet_v2.onnx, via yakhyo/face-anti-spoofing, from minivision-ai/Silent-Face-Anti-Spoofing)
    license_code: MIT (UniFace), Apache-2.0 (model source)
    license_weights: Apache-2.0 (training data undisclosed)
    runtime: both
    notes: Passive RGB; measured 3 ms CPU. No independent accuracy evidence. Production needs iBeta ISO/IEC 30107-3 tested PAD and NIR/3D hardware.
  - component: presentation attack detection (fallback)
    language: both
    repo_url: https://github.com/facenox/face-antispoof-onnx
    model: MiniFASNetV2-SE best_model_quantized.onnx (INT8, 600 KB)
    license_code: Apache-2.0
    license_weights: Apache-2.0 as stated, but trained on CelebA-Spoof (non-commercial research only)
    runtime: both
    notes: Measured 8.7 ms CPU; 98.20% on CelebA-Spoof (self-reported); not trained on 3D masks. Alternative is the insightface 2.0 liveness addon (addons=["liveness"], normal/observe gating), licence unstated so treat as non-commercial.
  - component: multi-face tracking
    language: Python
    repo_url: https://github.com/roboflow/trackers
    model: ByteTrackTracker (clean-room re-implementation; fallback UniFace BYTETracker)
    license_code: Apache-2.0
    license_weights: n/a
    runtime: CPU
    notes: 0.6 ms per update; kept 6 stable IDs over a 60-frame synthetic walk-through. No maintained Java tracker was found.
  - component: 1:N search
    language: both
    repo_url: https://github.com/facebookresearch/faiss
    model: IndexFlatIP exact search (or NumPy dot product); fallback pgvector 0.8.6 https://github.com/pgvector/pgvector
    license_code: MIT (FAISS) / PostgreSQL License (pgvector)
    license_weights: n/a
    runtime: CPU
    notes: 500x512 top-2 in 0.04 ms; 100k in 14 ms (2 vCPU). pgvector adds persistence and manifest-scoped SQL (Java com.pgvector:pgvector 0.1.6; Python pgvector 0.5.0). Threshold and top-2 margin rule to be calibrated in Phase 4.
  - component: resilience (Java)
    language: Java
    repo_url: https://github.com/resilience4j/resilience4j
    model: resilience4j-spring-boot4 2.4.0 on Spring Boot 4.1.1 (Java 21)
    license_code: Apache-2.0
    license_weights: n/a
    runtime: CPU
    notes: Validated. @CircuitBreaker opened after 3 failures, fell back to MANUAL_CHECK, and was exposed at /actuator/circuitbreakers and in Prometheus. Spring Framework 7 @Retryable/@ConcurrencyLimit have no breaker.
  - component: resilience (Python)
    language: Python
    repo_url: https://github.com/danielfm/pybreaker
    model: pybreaker 1.4.1 + tenacity 9.1.4 (https://github.com/jd/tenacity); stamina as an alternative for retries
    license_code: BSD-3 / Apache-2.0
    license_weights: n/a
    runtime: CPU
    notes: Validated in a FastAPI gate service. pybreaker async support needs Tornado, so use sync endpoints or a threadpool.
  - component: observability (Java)
    language: Java
    repo_url: https://github.com/spring-projects/spring-boot
    model: Actuator + micrometer-registry-prometheus + spring-boot-starter-opentelemetry (Boot 4.1.1); fallback OTel Java agent 2.31.1
    license_code: Apache-2.0
    license_weights: n/a
    runtime: CPU
    notes: Validated /actuator/prometheus with resilience4j and http_server_requests metrics. Add gate_decisions_total, score and margin histograms, and fallback rate.
  - component: observability (Python)
    language: Python
    repo_url: https://github.com/trallnag/prometheus-fastapi-instrumentator
    model: instrumentator 8.1.0 + prometheus_client 0.26.0 + structlog 26.1.0; fallback OpenTelemetry Python 1.45.0 + FastAPI instrumentation 0.66b0 (Beta)
    license_code: ISC / Apache-2.0 / MIT-Apache
    license_weights: n/a
    runtime: CPU
    notes: Validated /metrics with a custom gate_decisions_total counter.
  - component: integration / outbox (Java)
    language: Java
    repo_url: https://github.com/spring-projects/spring-modulith
    model: Spring Modulith 2.1.1 Event Publication Registry (JPA/JDBC); fallback Namastack Outbox 1.9.0 https://github.com/namastack/namastack-outbox
    license_code: Apache-2.0
    license_weights: n/a
    runtime: CPU
    notes: Keeps the boarded-state write and the gate event atomic. No maintained Python outbox or idempotency library was found, so a Python build would use a table plus a unique Idempotency-Key.
  - component: template protection
    language: Python
    repo_url: https://github.com/OpenMined/TenSEAL
    model: CKKS encrypted probe vs gallery (TenSEAL 0.3.18); fallback PolyProtect re-implemented from the paper
    license_code: Apache-2.0
    license_weights: n/a
    runtime: CPU
    notes: Validated correct top-1, but naive encrypted matmul over 500 templates took 2.65 s. Real time needs IDFace-style packing. OpenFHE-python is Linux-only via pip.
  - component: evaluation (fairness / error rates)
    language: Python
    repo_url: https://github.com/manuelaguadomtz/pyeer
    model: pyeer 0.5.5 metrics + BFW data (https://github.com/visionjo/facerec-bias-bfw)
    license_code: MIT / BSD-3
    license_weights: BFW data released for research purposes
    runtime: CPU
    notes: pyeer is stale but stable; compute per-group FNMR/FMR in the style of ISO/IEC 19795-10.
language_options:
  - option: all Python
    pros: Every model block pip-installs and was run end to end (InsightFace 2.0, UniFace, OpenCV 5, FAISS, trackers, FastAPI + pybreaker + Prometheus). Fastest to build in 2 days and matches the user's daily stack. GPU is straightforward via onnxruntime-gpu.
    cons: Does not exercise the Java/Spring/Hibernate target stack. Async circuit breaking is weak (pybreaker needs Tornado). No standard outbox or idempotency library. onnxruntime-gpu 1.30 wheels need CUDA 13 (1.26 for CUDA 12).
  - option: Java + Python
    pros: Spring Boot 4.1.1 gate service with Resilience4j, Actuator/Micrometer, OTel starter and Spring Modulith outbox (validated) directly mirrors the target enterprise stack, while the ML stays in Python where models and GPU support are mature. The same split exists in open source (CompreFace). Gives a clear diagram and failure-injection demo.
    cons: Two services, a REST/gRPC contract and two builds within 2 days. The extra network hop needs timeouts and must be explained. CompreFace itself is stale, so it is a pattern only, not reusable code.
  - option: all Java
    pros: Strongest literal fit with the Java target stack. ONNX Runtime Java 1.30.0 (CPU and GPU artifacts on Maven) runs the same ONNX models (SFace 14.7 ms, R50 146 ms CPU measured). OpenCV Java has native YuNet/SFace. Single JVM to operate.
    cons: SCRFD decoding, face alignment, ByteTrack and PAD cropping must be hand-ported. Prebuilt OpenCV Java lags (openpnp 4.9.0 Dec 2023; JavaCV 4.14.0). The CUDA version of onnxruntime_gpu for Java is undocumented. Highest risk of not finishing, and time goes to ML plumbing rather than system design.
rejected:
  - repo: https://github.com/mikel-brostrom/boxmot
    reason: AGPL-3.0 copyleft; roboflow/trackers (Apache-2.0) covers ByteTrack.
  - repo: https://github.com/exadel-inc/CompreFace
    reason: Stale (last commit 2023-11-14) on Spring Boot 2.5.13; kept only as an architectural precedent for Java + Python.
  - repo: https://github.com/ageitgey/face_recognition
    reason: dlib-era model with only an LFW 99.38% claim; Windows not officially supported; 775 open issues.
  - repo: https://github.com/SthPhoenix/InsightFace-REST
    reason: Last release 2021-11-06; superseded by the official InsightFace Server 0.3.1.
  - repo: https://github.com/ZitongYu/CDCN
    reason: Research-only licence ("commercial use is not allowed"), no pretrained weights, inactive since 2021.
  - repo: https://github.com/minivision-ai/Silent-Face-Anti-Spoofing
    reason: Unmaintained since 2020 (issue #135), Caffe/Baidu deployment, undisclosed training data. Use the maintained Apache-2.0 ONNX port via UniFace.
  - repo: https://github.com/YantaoShen/openBCT
    reason: Non-official reimplementation, no face code or weights, inactive since 2021. Keep BCT as a talking point only.
  - repo: https://github.com/prevso1088/open-set-biometrics
    reason: No licence file; gait-focused README with no face example.
  - repo: https://github.com/Simprints/Biometrics-SimPolyProtect
    reason: GPL-3.0 and Kotlin/Android only; re-implement PolyProtect from the paper instead.
  - repo: https://github.com/idiap/BTPBench
    reason: Research-only non-commercial licence; usable as an evaluation reference only.
  - repo: https://github.com/HyperInspire/InspireFace
    reason: Models are academic-only (commercial licence required); C++ SDK build; Java bindings are Android JNI.
  - repo: https://github.com/jmformenti/face-recognition-java
    reason: No licence, 6 stars, inactive since 2022-12.
  - repo: https://github.com/mardiros/purgatory
    reason: 4 stars, last commit 2024-11; too little adoption for a resilience dependency.
  - repo: https://github.com/arlyon/aiobreaker
    reason: Inactive (last commit 2021-12-27).
  - repo: https://github.com/snok/asgi-idempotency-header
    reason: Last release 2022-08-31, BSD-4-Clause advertising clause; a DB unique key on Idempotency-Key is simpler.
  - repo: https://github.com/fdbtrs/CR-FIQA
    reason: CC BY-NC 4.0 code and weights, PyTorch-only with Google Drive weights; eDifFIQA(T) ONNX (CC BY 4.0) is simpler for the POC.
  - repo: https://github.com/mk-minchul/caface
    reason: Inactive since 2023, PyTorch 1.10 with Google Drive weights; quality-weighted averaging of per-frame embeddings is enough for the POC.
</handoff>
