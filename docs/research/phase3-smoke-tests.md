# Phase 3 Smoke-Test Checklist (Windows desktop with an NVIDIA GPU)

**Purpose:** Confirm that every *primary* building block from `phase3-handoff.md` installs and runs on this machine. For each block, record CPU latency, GPU latency and memory, so that Phase 4 can size the POC and argue that it would work on CPU or edge hardware.

**Pre-validated:** Every script below was run on 30 Sep 2026 in a Linux cloud workspace (2 vCPU, no GPU; Python 3.11.15, Java 21, Maven 3.9). GPU steps there fell back to CPU as designed. The reference CPU numbers are in the last section.

**How Claude Code should run this:**
- Work in `D:\POC\smoke`, using PowerShell.
- Run the steps in order. After each step, check the **Pass** line.
- Every metric is appended to `results.jsonl`. Step 15 turns it into `results.md`.
- If a step fails, record the error text in `results.md` under "Failures" and continue with the next step.
- Do not change model licences or download models other than those named here.
- Keep the whole folder local. `t1.jpg` is InsightFace's bundled sample image, used for smoke tests only.

---

## Step 0: Record the machine
```powershell
New-Item -ItemType Directory -Force D:\POC\smoke | Out-Null; Set-Location D:\POC\smoke
nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv
nvidia-smi | Select-String "CUDA Version"
py -0p                     # list installed Pythons; use 3.11 or 3.12
java -version              # need 21 (Spring Boot 4.1.1 supports 17-26)
mvn -v                     # need Maven >= 3.6.3
docker --version           # optional (pgvector, InsightFace Server)
```
**Record** the GPU name, VRAM, driver and the "CUDA Version" line in `results.md`, because this fills the `[GPU MODEL]`/`[X] GB` placeholders in the project context.
**Pass:** `nvidia-smi` prints a GPU, Python 3.11 or 3.12 exists, and Java 21 and Maven are present. (If Java or Maven are missing, install Temurin 21 and Maven, or skip steps 9–10.)

## Step 1: Python environment
```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install insightface==2.0 opencv-python==5.0.0.93 faiss-cpu==1.15.1 uniface==4.0.0 trackers==2.6.1 supervision huggingface_hub psutil
# InsightFace pulls the CPU build of ONNX Runtime; swap it for the GPU build (never keep both):
pip uninstall -y onnxruntime
pip install "onnxruntime-gpu[cuda,cudnn]==1.30.0"
pip list | Select-String onnxruntime      # must show ONLY onnxruntime-gpu
```
- The `onnxruntime-gpu` 1.30.0 wheels require **CUDA 13** (`nvidia-cuda-runtime~=13.0`, `nvidia-cudnn-cu13`, taken from the PyPI wheel metadata).
- **If** `nvidia-smi` shows a CUDA version below 13.0, use the CUDA 12 line instead: `pip uninstall -y onnxruntime-gpu; pip install "onnxruntime-gpu[cuda,cudnn]==1.26.0"`.
- **Pass:** `python -c "import onnxruntime as o; print(o.__version__, o.get_available_providers())"` lists `CUDAExecutionProvider`.

Now create the shared helper files. Write each file exactly as shown.

`bench.py`
```python
"""Shared timing/memory helpers for the Phase 3 smoke tests."""
import os, time, json, statistics, subprocess, datetime
import psutil

def init_ort():
    """Load CUDA/cuDNN DLLs shipped by the nvidia-* pip wheels (onnxruntime-gpu[cuda,cudnn]) before any GPU session."""
    import onnxruntime as ort
    if "CUDAExecutionProvider" in ort.get_available_providers():
        try:
            ort.preload_dlls()
        except Exception as e:  # system-wide CUDA install is also fine
            print("preload_dlls warning:", e)
    return ort

def gpu_mem_mb():
    try:
        out = subprocess.run(["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"],
                             capture_output=True, text=True, timeout=10).stdout.strip().splitlines()
        return int(out[0]) if out else None
    except Exception:
        return None

def bench(fn, n=50, warmup=5):
    for _ in range(warmup):
        fn()
    ts = []
    for _ in range(n):
        t = time.perf_counter(); fn(); ts.append((time.perf_counter() - t) * 1000)
    ts.sort()
    return {"p50_ms": round(statistics.median(ts), 2), "p95_ms": round(ts[int(0.95 * (len(ts) - 1))], 2)}

def rss_mb():
    return round(psutil.Process(os.getpid()).memory_info().rss / 2**20, 1)

def record(step, device, metrics, path="results.jsonl"):
    row = {"ts": datetime.datetime.now().isoformat(timespec="seconds"), "step": step, "device": device,
           "rss_mb": rss_mb(), "gpu_mem_mb": gpu_mem_mb(), **metrics}
    with open(path, "a", encoding="utf-8") as f:
        f.write(json.dumps(row) + "\n")
    print(json.dumps(row))
```
`peakmem.py`
```python
"""Run a command and report its peak RSS (process + children). Usage: python peakmem.py <cmd...>"""
import sys, time, subprocess, psutil
p = subprocess.Popen(sys.argv[1:], shell=(sys.platform == "win32"))
proc, peak = psutil.Process(p.pid), 0
while p.poll() is None:
    try:
        rss = proc.memory_info().rss + sum(c.memory_info().rss for c in proc.children(recursive=True))
        peak = max(peak, rss)
    except psutil.Error:
        pass
    time.sleep(0.2)
print(f"PEAK_RSS_MB={peak / 2**20:.0f} exit={p.returncode}")
```
Copy the sample image:
```powershell
python -c "import insightface,os,shutil; shutil.copy(os.path.join(os.path.dirname(insightface.__file__),'data','images','t1.jpg'),'t1.jpg')"
```

## Step 2: Prove the GPU really runs
`gpu_check.py`
```python
"""Step 2: prove the CUDA execution provider really runs (not just 'available')."""
import sys
from pathlib import Path
from bench import init_ort
ort = init_ort()
print("onnxruntime", ort.__version__, "available:", ort.get_available_providers())
model = Path.home() / ".insightface" / "models" / "buffalo_l" / "w600k_r50.onnx"
if not model.exists():
    from insightface.app import FaceAnalysis
    FaceAnalysis(name="buffalo_l", allowed_modules=["detection", "recognition"], providers=["CPUExecutionProvider"]).prepare(ctx_id=-1)
try:
    s = ort.InferenceSession(str(model), providers=["CUDAExecutionProvider", "CPUExecutionProvider"])
    used = s.get_providers()
    print("session providers:", used)
    print("PASS GPU" if used[0] == "CUDAExecutionProvider" else "CPU ONLY - see fallback in step 1")
except Exception as e:
    print("FAIL creating CUDA session:", e); sys.exit(1)
```
```powershell
python gpu_check.py
```
**Pass:** `PASS GPU`. If it prints `CPU ONLY` or fails with a missing DLL, go back to the CUDA 12 fallback in Step 1. Also check that `nvidia-smi` lists a driver that supports the chosen CUDA major. Record which line (1.30/CUDA 13 or 1.26/CUDA 12) worked.

## Step 3: Detection + recognition, primary (SCRFD-10G + ArcFace R50, InsightFace 2.0)
Licence: code MIT, **weights non-commercial research only**.

`s03_insightface.py`
```python
"""Step 3: SCRFD-10G detection + ArcFace R50 (buffalo_l) on CPU and GPU."""
import sys, cv2, numpy as np, onnxruntime as ort
from insightface.app import FaceAnalysis
from insightface.model_zoo import get_model
from insightface.utils import face_align
from pathlib import Path
from bench import bench, record, init_ort
init_ort()
img = cv2.imread("t1.jpg")
root = Path.home() / ".insightface" / "models" / "buffalo_l"
devices = [("cpu", ["CPUExecutionProvider"])]
if "CUDAExecutionProvider" in ort.get_available_providers():
    devices.append(("gpu", ["CUDAExecutionProvider", "CPUExecutionProvider"]))
for dev, prov in devices:
    app = FaceAnalysis(name="buffalo_l", allowed_modules=["detection", "recognition"], providers=prov)
    app.prepare(ctx_id=0 if dev == "gpu" else -1, det_size=(640, 640))
    faces = app.get(img)
    record("s03_pipeline_6faces", dev, {"faces": len(faces), **bench(lambda: app.get(img), n=20)})
    det = get_model(str(root / "det_10g.onnx"), providers=prov); det.prepare(ctx_id=0 if dev == "gpu" else -1, input_size=(640, 640))
    bboxes, kps = det.detect(img, max_num=0, metric="default")
    record("s03_scrfd10g_det_640", dev, bench(lambda: det.detect(img, max_num=0, metric="default")))
    rec = get_model(str(root / "w600k_r50.onnx"), providers=prov); rec.prepare(ctx_id=0 if dev == "gpu" else -1)
    crop = face_align.norm_crop(img, landmark=kps[0])
    record("s03_arcface_r50_1face", dev, bench(lambda: rec.get_feat(crop)))
# Edge proxy: recognition model alone, CPU limited to 4 threads
so = ort.SessionOptions(); so.intra_op_num_threads = 4
s = ort.InferenceSession(str(root / "w600k_r50.onnx"), so, providers=["CPUExecutionProvider"])
x = np.random.rand(1, 3, 112, 112).astype(np.float32); name = s.get_inputs()[0].name
record("s03_arcface_r50_cpu4threads", "cpu-4t", bench(lambda: s.run(None, {name: x}), n=30))
e = app.get(img); E = np.stack([f.normed_embedding for f in e]); S = E @ E.T; np.fill_diagonal(S, -1)
print("PASS" if len(e) >= 5 and S.max() < 0.5 else "CHECK", "faces=", len(e), "max_impostor_cos=", round(float(S.max()), 3))
```
```powershell
python .\peakmem.py python s03_insightface.py
```
**Measure:** CPU and GPU p50/p95 for:
- the full pipeline (6 faces);
- SCRFD at 640 px;
- R50 on one face;
- R50 with CPU limited to 4 threads (an edge proxy).

Also record `PEAK_RSS_MB` and `gpu_mem_mb`. **Pass:** the last line starts with `PASS` (6 faces found, max impostor cosine < 0.5).

## Step 4: Detection + recognition, fallback (YuNet + SFace, OpenCV Zoo, MIT / Apache-2.0)
`s04_opencv_fallback.py`
```python
"""Step 4: fallback detector/recognizer YuNet + SFace (OpenCV Zoo, MIT / Apache-2.0) on CPU."""
import cv2 as cv, numpy as np
from huggingface_hub import hf_hub_download
from bench import bench, record
yunet = hf_hub_download("opencv/face_detection_yunet", "face_detection_yunet_2023mar.onnx")
sface = hf_hub_download("opencv/face_recognition_sface", "face_recognition_sface_2021dec.onnx")
img = cv.imread("t1.jpg"); h, w = img.shape[:2]
det = cv.FaceDetectorYN.create(yunet, "", (w, h), 0.8, 0.3, 5000)
rec = cv.FaceRecognizerSF.create(sface, "")
_, faces = det.detect(img)
record("s04_yunet_det", "cpu", {"faces": int(len(faces)), **bench(lambda: det.detect(img))})
crop = rec.alignCrop(img, faces[0])
record("s04_sface_1face", "cpu", bench(lambda: rec.feature(crop)))
f0 = rec.feature(crop); f1 = rec.feature(rec.alignCrop(img, faces[1]))
same = rec.match(f0, f0, cv.FaceRecognizerSF_FR_COSINE); diff = rec.match(f0, f1, cv.FaceRecognizerSF_FR_COSINE)
print("PASS" if len(faces) >= 5 and diff < 0.363 else "CHECK", "opencv", cv.__version__, "cos_same", round(same, 3), "cos_diff", round(diff, 3))
```
```powershell
python .\peakmem.py python s04_opencv_fallback.py
```
**Measure:** CPU p50/p95 for YuNet and SFace. OpenCV 5's new DNN engine ignores CUDA targets, so these run on CPU only. **Pass:** `PASS` (≥5 faces, impostor cosine below SFace's 0.363 threshold).

## Step 5: Quality, eDifFIQA(T) (OpenCV Zoo ONNX, CC BY 4.0)
`s05_quality.py`
```python
"""Step 5: face image quality eDifFIQA(T) (OpenCV Zoo ONNX, CC BY 4.0) via ONNX Runtime, CPU and GPU."""
import cv2, numpy as np, onnxruntime as ort
from huggingface_hub import hf_hub_download
from insightface.app import FaceAnalysis
from insightface.utils import face_align
from bench import bench, record, init_ort
init_ort()
model = hf_hub_download("opencv/face_image_quality_assessment_ediffiqa", "ediffiqa_tiny_jun2024.onnx")
img = cv2.imread("t1.jpg")
app = FaceAnalysis(name="buffalo_l", allowed_modules=["detection"], providers=["CPUExecutionProvider"]); app.prepare(ctx_id=-1)
crop = face_align.norm_crop(img, landmark=app.get(img)[0].kps)          # 112x112 aligned BGR
def prep(c):
    x = cv2.cvtColor(c, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    return ((x - 0.5) / 0.5).transpose(2, 0, 1)[None]
devices = [("cpu", ["CPUExecutionProvider"])]
if "CUDAExecutionProvider" in ort.get_available_providers():
    devices.append(("gpu", ["CUDAExecutionProvider", "CPUExecutionProvider"]))
for dev, prov in devices:
    s = ort.InferenceSession(model, providers=prov); n = s.get_inputs()[0].name
    q = float(s.run(None, {n: prep(crop)})[0].ravel()[0])
    qb = float(s.run(None, {n: prep(cv2.GaussianBlur(crop, (0, 0), 3))})[0].ravel()[0])
    record("s05_ediffiqa_t", dev, {"score": round(q, 3), "score_blurred": round(qb, 3), **bench(lambda: s.run(None, {n: prep(crop)}))})
print("PASS" if q > qb + 0.2 else "CHECK", "sharp", round(q, 3), "blurred", round(qb, 3))
```
```powershell
python .\peakmem.py python s05_quality.py
```
**Measure:** CPU and GPU p50/p95, plus the sharp and blurred scores. **Pass:** `PASS` (sharp score exceeds blurred by more than 0.2).

## Step 6: Presentation attack detection, MiniFASNet V2 via UniFace (Apache-2.0)
`s06_pad.py`
```python
"""Step 6: passive PAD MiniFASNet V2 via UniFace (weights Apache-2.0 per UniFace attribution)."""
import cv2
from uniface.detection import RetinaFace
from uniface.spoofing import MiniFASNet
from bench import bench, record
import sys
img = cv2.imread(sys.argv[1] if len(sys.argv) > 1 else "t1.jpg")
faces = RetinaFace().detect(img)
spoofer = MiniFASNet()
r = spoofer.predict(img, faces[0].bbox)
record("s06_minifasnet_v2", "auto", {"is_real": bool(r.is_real), "conf": round(float(r.confidence), 3), **bench(lambda: spoofer.predict(img, faces[0].bbox))})
print("RESULT", "is_real", r.is_real, "(expect True for t1.jpg, False for spoof.jpg)")
print("MANUAL: photograph t1.jpg displayed on a phone/monitor with your webcam, save as spoof.jpg, rerun: python s06_pad.py spoof.jpg; expect is_real=False.")
```
```powershell
python .\peakmem.py python s06_pad.py
# Manual spoof check: show t1.jpg full-screen on a phone, photograph it with a webcam or phone, save as spoof.jpg, then:
python s06_pad.py spoof.jpg
```
**Measure:** p50/p95 and `is_real` for both images. **Pass:** `t1.jpg` gives `True`. Record whatever `spoof.jpg` gives; a `True` there is a *finding* for the interview (a PAD limitation), not a setup failure.

## Step 7: Multi-face tracking, ByteTrack (roboflow/trackers, Apache-2.0)
`s07_tracking.py`
```python
"""Step 7: multi-face tracking - SCRFD detections -> ByteTrack (roboflow/trackers, Apache-2.0).
Synthetic 'walk-through': the sample image is shifted across 60 frames; each face should keep one track ID."""
import cv2, numpy as np, supervision as sv
from trackers import ByteTrackTracker
from insightface.app import FaceAnalysis
from bench import record
import time
img = cv2.imread("t1.jpg"); h, w = img.shape[:2]
app = FaceAnalysis(name="buffalo_l", allowed_modules=["detection"], providers=["CPUExecutionProvider"]); app.prepare(ctx_id=-1, det_size=(640, 640))
tracker = ByteTrackTracker()
ids_per_frame, t_track = [], []
for f in range(60):
    M = np.float32([[1, 0, 2 * f], [0, 1, f]])
    frame = cv2.warpAffine(img, M, (w, h), borderMode=cv2.BORDER_REPLICATE)
    faces = app.get(frame)
    det = sv.Detections(xyxy=np.array([fc.bbox for fc in faces], dtype=float),
                        confidence=np.array([fc.det_score for fc in faces], dtype=float),
                        class_id=np.zeros(len(faces), dtype=int))
    t = time.perf_counter(); tracked = tracker.update(det); t_track.append((time.perf_counter() - t) * 1000)
    ids_per_frame.append(set(int(i) for i in tracked.tracker_id if i is not None and i >= 0))
all_ids = set().union(*ids_per_frame[5:])
record("s07_bytetrack_update", "cpu", {"frames": 60, "unique_ids_after_warmup": len(all_ids), "p50_ms": round(float(np.median(t_track)), 3)})
print("PASS" if len(all_ids) == 6 else "CHECK", "unique track ids (expect 6):", len(all_ids))
```
```powershell
python s07_tracking.py
```
**Pass:** `PASS unique track ids (expect 6): 6`. **Measure:** tracker update p50 (detection time is already covered in Step 3).

## Step 8: 1:N search, exact inner product (FAISS / NumPy, MIT)
`s08_search.py`
```python
"""Step 8: 1:N search - exact inner product over a flight-sized gallery (FAISS IndexFlatIP vs NumPy) plus top-2 margin rule."""
import numpy as np, faiss
from bench import bench, record
rng = np.random.default_rng(0)
for N in (500, 100_000):
    G = rng.standard_normal((N, 512)).astype("float32"); faiss.normalize_L2(G)
    idx = faiss.IndexFlatIP(512); idx.add(G)
    q = G[:1] + 0.05 * rng.standard_normal((1, 512)).astype("float32"); faiss.normalize_L2(q)
    D, I = idx.search(q, 2)
    record(f"s08_faiss_flatip_N{N}", "cpu", {"top1": int(I[0, 0]), "margin": round(float(D[0, 0] - D[0, 1]), 3), **bench(lambda: idx.search(q, 2), n=200)})
G5 = G[:500]
record("s08_numpy_exact_N500", "cpu", bench(lambda: np.argpartition(-(G5 @ q[0]), 2)[:2], n=500))
T, DELTA = 0.35, 0.10   # placeholder threshold / margin - calibrate in Phase 4
decide = lambda s: "MATCH" if s[0] >= T and s[0] - s[1] >= DELTA else "MANUAL_CHECK"
print("PASS" if int(I[0, 0]) == 0 else "CHECK", "decision:", decide(D[0]))
```
```powershell
python s08_search.py
```
**Pass:** `PASS decision: MATCH`. **Measure:** p50/p95 for N=500 (a flight gallery) and N=100k, plus peak RSS.

## Step 9: Java inference, ONNX Runtime Java 1.30.0 (CPU, then GPU)
Create `java-ort\pom.xml` and `java-ort\src\main\java\poc\OrtSmoke.java`:

`java-ort\pom.xml`
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>poc</groupId><artifactId>ort-smoke</artifactId><version>1.0</version>
  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <ort.version>1.30.0</ort.version>
    <ort.artifact>onnxruntime</ort.artifact>
  </properties>
  <profiles>
    <!-- mvn -Pgpu ... switches to the CUDA build of ONNX Runtime Java -->
    <profile><id>gpu</id><properties><ort.artifact>onnxruntime_gpu</ort.artifact></properties></profile>
  </profiles>
  <dependencies>
    <dependency><groupId>com.microsoft.onnxruntime</groupId><artifactId>${ort.artifact}</artifactId><version>${ort.version}</version></dependency>
  </dependencies>
  <build><plugins><plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>3.5.0</version>
    <configuration><mainClass>poc.OrtSmoke</mainClass></configuration></plugin></plugins></build>
</project>
```
`java-ort\src\main\java\poc\OrtSmoke.java`
```java
package poc;
import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.util.*;
/** Step 9: load an ONNX face model with ONNX Runtime Java and time single-face inference. Args: <model.onnx> [cpu|gpu] */
public class OrtSmoke {
  public static void main(String[] a) throws Exception {
    String model = a[0]; boolean gpu = a.length > 1 && a[1].equalsIgnoreCase("gpu");
    OrtEnvironment env = OrtEnvironment.getEnvironment();
    OrtSession.SessionOptions so = new OrtSession.SessionOptions();
    if (gpu) so.addCUDA(0);
    long t0 = System.nanoTime();
    try (OrtSession s = env.createSession(model, so)) {
      double loadMs = (System.nanoTime() - t0) / 1e6;
      String in = s.getInputNames().iterator().next();
      long[] shape = ((TensorInfo) s.getInputInfo().get(in).getInfo()).getShape();
      for (int i = 0; i < shape.length; i++) if (shape[i] < 0) shape[i] = (i == 0) ? 1 : 112;
      int n = 1; for (long d : shape) n *= d;
      float[] x = new float[n]; Random r = new Random(0); for (int i = 0; i < n; i++) x[i] = r.nextFloat() * 2 - 1;
      try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), shape)) {
        int dim = ((float[][]) s.run(Map.of(in, t)).get(0).getValue())[0].length;
        for (int i = 0; i < 10; i++) s.run(Map.of(in, t)).close();
        double[] ms = new double[50];
        for (int i = 0; i < ms.length; i++) { long t1 = System.nanoTime(); s.run(Map.of(in, t)).close(); ms[i] = (System.nanoTime() - t1) / 1e6; }
        Arrays.sort(ms);
        System.out.printf("RESULT device=%s providers=%s load_ms=%.0f out_dim=%d p50_ms=%.2f p95_ms=%.2f heap_mb=%d%n",
            gpu ? "gpu" : "cpu", OrtEnvironment.getAvailableProviders(), loadMs, dim, ms[25], ms[47],
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20);
      }
    }
  }
}
```
```powershell
Set-Location java-ort
Copy-Item "$env:USERPROFILE\.cache\huggingface\hub\models--opencv--face_recognition_sface\snapshots\*\face_recognition_sface_2021dec.onnx" sface.onnx
Copy-Item "$env:USERPROFILE\.insightface\models\buffalo_l\w600k_r50.onnx" r50.onnx
python ..\peakmem.py mvn -q -B compile exec:java "-Dexec.args=sface.onnx cpu"
python ..\peakmem.py mvn -q -B compile exec:java "-Dexec.args=r50.onnx cpu"
python ..\peakmem.py mvn -q -B -Pgpu compile exec:java "-Dexec.args=r50.onnx gpu"
Set-Location ..
```
- Maven puts the whole POM on the JVM classpath, so the CPU and GPU runs use separate profiles (`-Pgpu` swaps in `onnxruntime_gpu`).
- ONNX Runtime does not document which CUDA major the Java GPU jar needs. If the GPU run fails, record the error text (it names the missing `cudart`/`cudnn` DLL, which tells you the version) and continue.

**Measure:** the `RESULT` line (p50/p95, load time, heap) and `PEAK_RSS_MB`. Append them to `results.md` by hand, since this step does not write to `results.jsonl`.

## Step 10: Java gate service, Spring Boot 4.1.1 + Resilience4j 2.4.0 + Actuator/Prometheus
Create the files under `gate-java\`:

`gate-java\pom.xml`
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>4.1.1</version></parent>
  <groupId>poc</groupId><artifactId>gate-smoke</artifactId><version>1.0</version>
  <properties><java.version>21</java.version></properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-aspectj</artifactId></dependency>
    <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot4</artifactId><version>2.4.0</version></dependency>
    <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>
  </dependencies>
  <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
```
`gate-java\src\main\java\poc\GateApp.java`
```java
package poc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@SpringBootApplication public class GateApp { public static void main(String[] a){ SpringApplication.run(GateApp.class,a);} }
@Service class MatcherClient {
  volatile boolean down = true; // simulate model service / identity provider outage
  @CircuitBreaker(name = "matcher", fallbackMethod = "manualLane")
  public Map<String,Object> match(String gateId) { if (down) throw new IllegalStateException("matcher unavailable"); return Map.of("gate", gateId, "decision", "MATCH"); }
  Map<String,Object> manualLane(String gateId, Throwable t) { return Map.of("gate", gateId, "decision", "MANUAL_CHECK", "reason", t.getClass().getSimpleName()); }
}
@RestController class GateController {
  private final MatcherClient m; GateController(MatcherClient m){this.m=m;}
  @PostMapping("/gates/{id}/events") Map<String,Object> event(@PathVariable String id){ return m.match(id); }
}
```
`gate-java\src\main\resources\application.yml`
```yaml
server.port: 18080
management:
  endpoints.web.exposure.include: health,metrics,prometheus,circuitbreakers
  health.circuitbreakers.enabled: true
resilience4j.circuitbreaker.instances.matcher:
  sliding-window-size: 5
  minimum-number-of-calls: 3
  failure-rate-threshold: 50
  wait-duration-in-open-state: 10s
  register-health-indicator: true
```
```powershell
Set-Location gate-java; mvn -q -B -DskipTests package
$p = Start-Process java -ArgumentList '-jar','target\gate-smoke-1.0.jar' -PassThru -NoNewWindow
Start-Sleep 12
1..5 | ForEach-Object { Invoke-RestMethod -Method Post http://localhost:18080/gates/A12/events | ConvertTo-Json -Compress }
Invoke-RestMethod http://localhost:18080/actuator/circuitbreakers | ConvertTo-Json -Depth 5
(Invoke-WebRequest http://localhost:18080/actuator/prometheus).Content -split "`n" | Select-String 'resilience4j_circuitbreaker_state|http_server_requests_seconds_count'
"{0:N0} MB" -f ((Get-Process -Id $p.Id).WorkingSet64/1MB)
Stop-Process -Id $p.Id; Set-Location ..
```
**Pass:** calls 1–3 return `MANUAL_CHECK` with `IllegalStateException`, calls 4–5 with `CallNotPermittedException`, and `/actuator/circuitbreakers` shows `"state":"OPEN"`. **Measure:** startup time (the "Started GateApp in X seconds" log line) and working set in MB.

## Step 11: Python gate service, FastAPI + pybreaker + tenacity + Prometheus + structlog
```powershell
pip install fastapi uvicorn pybreaker==1.4.1 tenacity prometheus-fastapi-instrumentator structlog
```
`gate_py.py`
```python
import pybreaker, structlog
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator
from prometheus_client import Counter
from tenacity import retry, stop_after_attempt, wait_exponential_jitter, retry_if_exception_type
log = structlog.get_logger()
app = FastAPI()
Instrumentator().instrument(app).expose(app)
DECISIONS = Counter("gate_decisions_total", "Gate decisions", ["gate", "decision"])
breaker = pybreaker.CircuitBreaker(fail_max=3, reset_timeout=10)
DOWN = True
@retry(stop=stop_after_attempt(2), wait=wait_exponential_jitter(initial=0.05, max=0.2), retry=retry_if_exception_type(ConnectionError), reraise=True)
def call_matcher(gate):
    if DOWN: raise ConnectionError("matcher unavailable")
    return "MATCH"
@app.post("/gates/{gate}/events")
def event(gate: str):
    try:
        d = breaker.call(call_matcher, gate); reason = None
    except (pybreaker.CircuitBreakerError, ConnectionError) as e:
        d, reason = "MANUAL_CHECK", type(e).__name__
    DECISIONS.labels(gate, d).inc(); log.info("gate_decision", gate=gate, decision=d, reason=reason, breaker=breaker.current_state)
    return {"gate": gate, "decision": d, "reason": reason, "breaker": breaker.current_state}
```
```powershell
$p = Start-Process python -ArgumentList '-m','uvicorn','gate_py:app','--port','18081' -PassThru -NoNewWindow
Start-Sleep 5
1..5 | ForEach-Object { Invoke-RestMethod -Method Post http://localhost:18081/gates/A12/events | ConvertTo-Json -Compress }
(Invoke-WebRequest http://localhost:18081/metrics).Content -split "`n" | Select-String '^gate_decisions_total'
Stop-Process -Id $p.Id
```
**Pass:** calls 1–2 return `ConnectionError` with the breaker `closed`, calls 3–5 return `CircuitBreakerError` with the breaker `open`, and `gate_decisions_total{decision="MANUAL_CHECK",gate="A12"} 5.0`.

## Step 12 (optional): Encrypted matching, TenSEAL CKKS (Apache-2.0)
```powershell
pip install tenseal==0.3.18
```
`s12_tenseal.py`
```python
"""Optional step 12: encrypted probe vs plaintext flight gallery (CKKS, TenSEAL, Apache-2.0).
The gate encrypts the probe; the matcher computes encrypted dot products; only the key holder decrypts scores."""
import time, numpy as np, tenseal as ts
from bench import record
rng = np.random.default_rng(0)
G = rng.standard_normal((500, 512)); G /= np.linalg.norm(G, axis=1, keepdims=True)
q = G[7] + 0.05 * rng.standard_normal(512); q /= np.linalg.norm(q)
ctx = ts.context(ts.SCHEME_TYPE.CKKS, poly_modulus_degree=8192, coeff_mod_bit_sizes=[60, 40, 40, 60])
ctx.global_scale = 2**40; ctx.generate_galois_keys()
t = time.perf_counter(); enc_q = ts.ckks_vector(ctx, q.tolist()); t_enc = (time.perf_counter() - t) * 1000
t = time.perf_counter(); enc_scores = enc_q.matmul(G.T.tolist()); t_mm = (time.perf_counter() - t) * 1000
scores = np.array(enc_scores.decrypt()); plain = G @ q
record("s12_tenseal_ckks_N500", "cpu", {"encrypt_ms": round(t_enc, 1), "enc_matmul_ms": round(t_mm, 1),
       "top1": int(scores.argmax()), "max_abs_err": float(np.abs(scores - plain).max())})
print("PASS" if int(scores.argmax()) == 7 else "CHECK", "top1", int(scores.argmax()))
```
```powershell
python s12_tenseal.py
```
**Pass:** `PASS top1 7`. **Measure:** `enc_matmul_ms`. Expect seconds, not milliseconds, with this naive layout; that gap is a talking point for P10/P19.

## Step 13 (optional, heavy): OFIQ 1.2.0 conformant build (Windows)
Not validated in the cloud workspace. It needs CMake ≥3.26, Conan 2.18.1, Visual Studio C++ tools and a 422 MB model download from the ISO portal.
```powershell
pip install conan==2.18.1
git clone --depth 1 --branch v1.2.0 https://github.com/BSI-OFIQ/OFIQ-Project.git
Set-Location OFIQ-Project\scripts; .\build.cmd; Set-Location ..\..
```
**Measure:** build duration. Then run the `OFIQSampleApp` binary produced by the build (see BUILD.md for its path and arguments) on `t1.jpg`. **Pass:** the unified quality score and component scores print. If the build fails within 30 minutes, stop and record the failure; eDifFIQA (Step 5) is the POC primary anyway.

## Step 14 (optional): pgvector gallery in PostgreSQL (Docker)
```powershell
docker run -d --name pgv -e POSTGRES_PASSWORD=poc -p 5433:5432 pgvector/pgvector:pg17
Start-Sleep 8
docker exec pgv psql -U postgres -c "CREATE EXTENSION vector; CREATE TABLE gallery(id int, flight text, emb vector(512)); INSERT INTO gallery SELECT i, 'EK001', (SELECT array_agg(random()-0.5)::vector(512) FROM generate_series(1,512) WHERE i>0) FROM generate_series(1,500) i;"
docker exec pgv psql -U postgres -c "\timing on" -c "SELECT id, 1-(emb <=> (SELECT emb FROM gallery WHERE id=7)) AS cos FROM gallery WHERE flight='EK001' ORDER BY emb <=> (SELECT emb FROM gallery WHERE id=7) LIMIT 2;"
docker rm -f pgv
```
**Pass:** top row `id=7`, cos ≈ 1.0. **Measure:** query time from `\timing` (exact scan over 500 rows). Validated in the cloud workspace: the image ships pgvector 0.8.6; the query took 5.3 ms.

## Step 15: Summarise
`summarize.py`
```python
"""Step 15: turn results.jsonl into a markdown table (results.md) for Phase 4."""
import json
rows = [json.loads(l) for l in open("results.jsonl", encoding="utf-8") if l.strip()]
cols = ["step", "device", "p50_ms", "p95_ms", "rss_mb", "gpu_mem_mb"]
extra = sorted({k for r in rows for k in r} - set(cols) - {"ts"})
out = ["| " + " | ".join(cols + extra) + " |", "|" + "---|" * (len(cols) + len(extra))]
for r in rows:
    out.append("| " + " | ".join(str(r.get(c, "")) for c in cols + extra) + " |")
open("results.md", "w", encoding="utf-8").write("\n".join(out) + "\n")
print("\n".join(out))
```
```powershell
python summarize.py
```
Then append to `results.md`:
- the Step 0 machine facts;
- the Java `RESULT` and `PEAK_RSS_MB` lines (Steps 9–10);
- any failures.

Copy `results.md` to `D:\POC\docs\research\phase3-smoke-results.md` for Phase 4.

---

## Reference CPU numbers from the cloud workspace (2 vCPU, no GPU, 30 Sep 2026)

| Block | p50 (ms) | Notes |
|---|---|---|
| InsightFace pipeline, 6 faces (SCRFD-10G@640 + 6× R50) | 1099 | peak RSS ~0.6–1.2 GB |
| SCRFD-10G detection @640 | 130–135 | |
| ArcFace R50, one face (Python ORT / Java ORT) | 138 / 146 | |
| YuNet detection / SFace one face (OpenCV 5.0) | 36 / 11 | SFace via Java ORT 14.7 |
| eDifFIQA(T) | 8.8 | sharp 0.707 vs blurred 0.210 |
| MiniFASNet V2 (UniFace) / facenox INT8 | 3.4 / 8.7 | |
| InsightFace liveness addon | +~70 per 6-face frame | |
| ByteTrack update | 0.6 | 6 stable IDs over 60 frames |
| FAISS FlatIP N=500 / N=100k | 0.04 / 13.8 | NumPy N=500: 0.04 |
| TenSEAL CKKS encrypted probe × 500 | 2648 | naive `matmul` |
| Spring Boot gate service startup | 4.4 s | fat jar 27 MB |
| pgvector exact top-2 over 500 rows (SQL) | 5.3 | Docker `pgvector/pgvector:pg17`, extension 0.8.6 |
