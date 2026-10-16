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
