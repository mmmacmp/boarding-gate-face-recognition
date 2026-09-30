<handoff phase="2">
shortlisted_approaches:
  - id: A1
    problem_ids: [P1, P2, P18]
    name: Pretrained margin-loss face embedder with a server-vs-edge comparison (AdaFace/ArcFace R50-R100 vs EdgeFace or MobileFaceNet)
    paper_title: "AdaFace: Quality Adaptive Margin for Face Recognition" (Kim, Jain, Liu, CVPR 2022); baselines ArcFace (CVPR 2019) and EdgeFace (IEEE TBIOM 2024)
    paper_url: https://arxiv.org/abs/2204.00964
    year: 2022
    why_shortlisted: Standard open recognizer family. AdaFace R100/WebFace12M reports IJB-C 97.66 @FAR 1e-4, EdgeFace 94.85 with 1.77M params, so the POC can show the accuracy vs edge-cost trade-off. Newer LVFace (ICCV 2025) is stronger but heavier and non-commercial.
    feasibility_note: GPU Y (inference and eval). CPU/edge Y via ONNX Runtime for R50, EdgeFace and MobileFaceNet. Open weights are non-commercial, so POC use only.
  - id: A2
    problem_ids: [P1, P8, P13]
    name: Manifest-scoped open-set 1:N search with boarded-state exclusion, FPIR-calibrated threshold and top-2 score-margin rule
    paper_title: NIST IR 8381 (FRVT identification for paperless travel; 420-person boarding galleries, FPIR 0.0003) plus "The Faiss library" (Douze et al., 2024) for exact search; optional open-set loss (Su et al., ECCV 2024)
    paper_url: https://nvlpubs.nist.gov/nistpubs/ir/2021/NIST.IR.8381.pdf
    year: 2021
    why_shortlisted: Directly models P1. The NIST FRTE Sep 2026 update shows median FNIR rising from 0.61% (640k) to 10.34% (12M), so gallery scoping is the key lever. In-gallery false positives among relatives and twins motivate the margin rule, and CBP TVS uses manifest galleries in production.
    feasibility_note: No GPU needed. Exact inner-product search over ~300-600 identities is sub-millisecond on CPU [INFERRED]. FAISS flat index runs on CPU or GPU.
  - id: A3
    problem_ids: [P4, P2]
    name: ISO/IEC 29794-5 quality gating via OFIQ plus a learned utility FIQA, used for capture feedback and best-shot selection rather than hard rejection
    paper_title: ISO/IEC 29794-5:2025 with OFIQ reference implementation (BSI); CR-FIQA (Boutros et al., CVPR 2023); FROQ training-free FIQA (arXiv Sep 2025)
    paper_url: https://github.com/BSI-OFIQ/OFIQ-Project
    year: 2025
    why_shortlisted: Current standard (Apr 2025, revision under way). DHS (Jan 2026), NIST FATE Quality and UNHCR show quality scores poorly predict match failure, which gives a strong senior talking point on feedback vs rejection.
    feasibility_note: OFIQ is C/C++ on CPU (mobile build exists). CR-FIQA (ResNet) runs on GPU or CPU. FROQ reuses the recognizer's features, so its extra cost is near zero [INFERRED].
  - id: A4
    problem_ids: [P6]
    name: Passive single-frame RGB presentation attack detection evaluated with ISO/IEC 30107-3 metrics (APCER/BPCER)
    paper_title: "Searching Central Difference Convolutional Networks for Face Anti-Spoofing" (CDCN, Yu et al., CVPR 2020); test standard ISO/IEC 30107-3:2023
    paper_url: https://arxiv.org/abs/2003.04092
    year: 2020
    why_shortlisted: Shows PAD awareness and correct reporting in a gate pipeline. Production uses iBeta-certified PAD and often NIR/3D cameras, so the POC should present this as a placeholder with the right interface and metrics. VLM-based FAS (FLIP, CFPL-FAS) is too heavy for edge.
    feasibility_note: GPU Y. Lightweight RGB PAD models run on CPU in real time [INFERRED]. Accuracy across domains is weak, so it is a demo only.
  - id: A5
    problem_ids: [P2, P16]
    name: Walk-through video pipeline (multi-face detection, tracking, per-track quality-weighted aggregation, one decision per person, multi-face/tailgating flag)
    paper_title: "ByteTrack: Multi-Object Tracking by Associating Every Detection Box" (Zhang et al., ECCV 2022) with SCRFD (Guo et al., ICLR 2022) and CAFace aggregation (NeurIPS 2022) as the upgrade path
    paper_url: https://arxiv.org/abs/2110.06864
    year: 2022
    why_shortlisted: DHS Rally 2022 found 97% of walk-through errors came from acquisition, and NIST FIVE 2024 shows boarding-gate video is now near-solved for good algorithms. The value is in capture logic, and the pipeline also covers the multi-face/tailgating concern (P16).
    feasibility_note: GPU Y. SCRFD small variants and ByteTrack run on CPU, with SCRFD near real time at gate resolution [INFERRED]. Aggregation cost is negligible.
  - id: A6
    problem_ids: [P10, P19, P12]
    name: Consent-gated matching with protected templates (search only the consented manifest gallery, transient in-memory probes, purge/TTL, pseudonymous decision logs, cancelable or HE-protected templates)
    paper_title: "IDFace: Face Template Protection for Efficient and Secure Identification" (Kim et al., ICCV 2025) plus EDPB Opinion 11/2024 and CBP TVS purge rules; cancelable benchmark (Otroshi Shahreza et al., 2026)
    paper_url: https://openaccess.thecvf.com/content/ICCV2025/html/Kim_IDFace_Face_Template_Protection_for_Efficient_and_Secure_Identification_ICCV_2025_paper.html
    year: 2025
    why_shortlisted: Fits an IT Group Security role and an airline integrating with an external identity provider (GDRFA). HE search of 1M templates took 126 ms, and template inversion (AAAI 2026) shows embeddings are sensitive. The EDPB and CBP give concrete design constraints.
    feasibility_note: No GPU needed. Cancelable transforms are trivial on CPU. HE matching over a flight-size gallery should be feasible on CPU with TenSEAL/OpenFHE [INFERRED], but IDFace hardware is not stated.
  - id: A7
    problem_ids: [P13, P14]
    name: Resilient event-driven gate integration (idempotent gate events, transactional outbox for boarded state, circuit breakers and timeouts, degraded modes from cached gallery to manual lane)
    paper_title: Government-anchored matching architecture (CBP TVS, Federal Register 2025-19655) with transactional outbox (microservices.io), IETF Idempotency-Key draft-07 and circuit breaker (Fowler 2014 / Resilience4j)
    paper_url: https://www.federalregister.gov/documents/2025/10/27/2025-19655/collection-of-biometric-data-from-aliens-upon-entry-to-and-departure-from-the-united-states
    year: 2025
    why_shortlisted: Covers two of the top five problems where Emirates IT has the most ownership, and maps directly to the JD (Java/Spring microservices, REST, Resilience4j). EES 2026 suspensions and the Sep 2025 Collins MUSE outage show degraded modes must be designed and tested.
    feasibility_note: No GPU needed. Plain services on CPU, and fault injection (GDRFA timeout, camera loss) is demonstrable locally.
  - id: A8
    problem_ids: [P15]
    name: ML-aware observability and safe matcher rollout (OpenTelemetry metrics and traces, golden signals per gate, score and top-2 margin distributions, manual-fallback rate as FNIR proxy, shadow/canary with template-version tagging)
    paper_title: "The ML Test Score: A Rubric for ML Production Readiness and Technical Debt Reduction" (Breck et al., 2017) with Google SRE four golden signals; optional BCT (Shen et al., CVPR 2020) for upgrades without re-extraction
    paper_url: https://research.google/pubs/the-ml-test-score-a-rubric-for-ml-production-readiness-and-technical-debt-reduction/
    year: 2017
    why_shortlisted: The JD stresses logging, monitoring hooks, on-call and SLAs. FRTE shows matchers are resubmitted constantly, so upgrade and re-templating is a real operational risk, and it involves GDRFA-held references.
    feasibility_note: No GPU needed. OTel collector with Prometheus/Grafana on CPU. BCT demo would need GPU fine-tuning (optional).
  - id: A9
    problem_ids: [P7]
    name: Demographic differential evaluation per ISO/IEC 19795-10 (FNMR/FMR differentials with uncertainty) of the POC recognizer on a public labelled benchmark
    paper_title: ISO/IEC 19795-10:2024, with FairCal (Salvador et al., ICLR 2022) as optional mitigation
    paper_url: https://www.iso.org/standard/81223.html
    year: 2024
    why_shortlisted: NIST FRTE 1:1 (Sep 2026) still finds West African and female subjects highest-disparity for almost all algorithms. A small, standards-based evaluation gives a credible talking point without claiming to fix bias.
    feasibility_note: GPU Y for embedding extraction. CPU fine for metric computation. Dataset licences must be checked in Phase 3.
search_terms_for_repos: ["insightface model_zoo onnx", "AdaFace", "EdgeFace", "LVFace", "OFIQ-Project", "CR-FIQA", "FROQ face image quality", "ViT-FIQA", "Silent-Face-Anti-Spoofing MiniFASNet", "CDCN face anti-spoofing", "SCRFD onnx", "ByteTrack", "caface", "faiss", "open-set-biometrics", "PolyProtect", "TenSEAL", "OpenFHE", "openBCT backward compatible", "FairCal", "Racial Faces in the Wild RFW", "Balanced Faces in the Wild BFW", "pyeer biometric evaluation", "APCER BPCER evaluation", "resilience4j spring boot", "spring modulith outbox", "debezium outbox", "opentelemetry java agent", "face recognition gate demo fastapi"]
</handoff>
