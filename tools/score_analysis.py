"""Genuine vs impostor score analysis for the POC001 gallery, and a data-driven threshold proposal.

Gallery = the references seed.py enrols on POC001 (volunteers + synthetic passengers that pass the enrolment check).
Probes  = every enrolled profile's probe.* files (mated), plus data/synthetic/STRANGER (never enrolled).
For each mated probe:  genuine = score vs its own reference; best non-mate = highest score vs anyone else.
  The best non-mate is BOTH the runner-up (top-2) when the passenger is enrolled, AND the top-1 an impostor would
  get if this passenger were not enrolled (leave-one-out), so it is the "impostor top-1" distribution.
For STRANGER probes: impostor top-1 = highest score vs the whole gallery.

Proposal rule: T = (highest impostor top-1 seen) + 0.02 safety gap, rounded UP to 2 decimals.
IMPORTANT: mostly SYNTHETIC faces (DigiFace-1M) plus one volunteer. This shows the METHOD, not operational numbers.
Writes docs/score-analysis.md and docs/img/score-histogram.png (aggregates only: no images, no embeddings).
Needs the model-service running (MODEL=r50). Usage: python tools/score_analysis.py
"""
import argparse
import math
from pathlib import Path

import cv2
import httpx
import numpy as np

from photos import REFERENCE_MIN_FACE_PX, clean_jpeg, discover_volunteers

ROOT = Path(__file__).resolve().parents[1]
PLACEHOLDER_T, MARGIN = 0.35, 0.10


def embed(client: httpx.Client, path: Path) -> tuple[np.ndarray | None, float]:
    faces = client.post("/v1/embed", content=clean_jpeg(path), headers={"Content-Type": "image/jpeg"}).json()["faces"]
    if len(faces) != 1:
        return None, 0.0
    return np.array(faces[0]["embedding"], np.float32), faces[0]["bbox"][2] - faces[0]["bbox"][0]


def stats(values: np.ndarray) -> str:
    p = np.percentile(values, [0, 5, 50, 95, 100])
    return " | ".join(f"{x:.3f}" for x in p)


def histogram_png(genuine: np.ndarray, impostor: np.ndarray, proposed: float, out: Path) -> None:
    """Grouped-bar histogram drawn with OpenCV (no plotting dependency). Palette slots 1-2, validated."""
    W, H, left, right, top, bottom = 1000, 460, 70, 30, 60, 60
    surface, ink, muted, grid = (251, 252, 252), (11, 11, 11), (78, 81, 82), (230, 230, 228)
    blue, orange = (214, 120, 42), (52, 104, 235)  # #2a78d6, #eb6834 in BGR
    img = np.full((H, W, 3), surface, np.uint8)
    edges = np.arange(-0.2, 1.0001, 0.05)
    g, _ = np.histogram(genuine, edges)
    i, _ = np.histogram(impostor, edges)
    ymax = max(g.max(), i.max())
    ymax = int(math.ceil(ymax / 5) * 5) + 5  # one grid step of headroom for the threshold labels
    x_of = lambda v: int(left + (v - edges[0]) / (edges[-1] - edges[0]) * (W - left - right))
    y_of = lambda c: int(H - bottom - c / ymax * (H - top - bottom))
    font = cv2.FONT_HERSHEY_SIMPLEX
    for c in range(0, ymax + 1, 5):  # recessive grid + y labels
        cv2.line(img, (left, y_of(c)), (W - right, y_of(c)), grid, 1)
        cv2.putText(img, str(c), (left - 35, y_of(c) + 5), font, 0.45, muted, 1, cv2.LINE_AA)
    for k in range(len(edges) - 1):  # two bars per bin, 2 px gap between them
        x0, x1 = x_of(edges[k]) + 3, x_of(edges[k + 1]) - 3
        mid = (x0 + x1) // 2
        for count, (a, b), colour in ((i[k], (x0, mid - 1), orange), (g[k], (mid + 1, x1), blue)):
            if count:
                cv2.rectangle(img, (a, y_of(count)), (b, H - bottom), colour, -1)
    cv2.line(img, (left, H - bottom), (W - right, H - bottom), muted, 1)
    for v in np.arange(-0.2, 1.0001, 0.1):
        cv2.putText(img, f"{v:.1f}", (x_of(v) - 14, H - bottom + 20), font, 0.45, muted, 1, cv2.LINE_AA)
    cv2.putText(img, "cosine score (ArcFace R50)", (W // 2 - 110, H - 15), font, 0.5, muted, 1, cv2.LINE_AA)
    cv2.putText(img, "probes", (8, top - 15), font, 0.45, muted, 1, cv2.LINE_AA)
    for t, label in ((PLACEHOLDER_T, f"placeholder T={PLACEHOLDER_T:.2f}"), (proposed, f"proposed T={proposed:.2f}")):
        x = x_of(t)
        for y in range(top, H - bottom, 10):  # dashed threshold line
            cv2.line(img, (x, y), (x, min(y + 5, H - bottom)), ink, 1)
        width = cv2.getTextSize(label, font, 0.45, 1)[0][0]
        x_text = x - width - 6 if t == PLACEHOLDER_T else x + 6  # labels on opposite sides so they never cross a line
        cv2.putText(img, label, (x_text, top + 12), font, 0.45, ink, 1, cv2.LINE_AA)
    cv2.putText(img, "Genuine vs impostor top-1 scores, POC001 gallery (mostly synthetic faces)", (left, 28),
                font, 0.6, ink, 1, cv2.LINE_AA)
    for n, (colour, label) in enumerate(((blue, f"genuine (n={len(genuine)})"),
                                          (orange, f"impostor top-1 (n={len(impostor)})"))):
        y = top + 10 + n * 22
        cv2.rectangle(img, (W - 260, y - 10), (W - 248, y + 2), colour, -1)
        cv2.putText(img, label, (W - 240, y), font, 0.45, ink, 1, cv2.LINE_AA)
    out.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(out), img)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model-service", default="http://localhost:8001")
    ap.add_argument("--data", type=Path, default=ROOT / "data")
    args = ap.parse_args()

    with httpx.Client(base_url=args.model_service, timeout=30) as ms:
        model_id = ms.get("/health").json()["model_id"]
        profiles = discover_volunteers(args.data) + discover_volunteers(args.data, "synthetic")
        gallery, probes = {}, []
        for v in profiles:
            if v.profile_id == "STRANGER":
                probes += [("STRANGER", p) for p in v.probes]
                continue
            template, width = embed(ms, v.reference) if v.reference else (None, 0)
            is_synthetic = v.reference is not None and "synthetic" in v.reference.parts
            if template is None or (is_synthetic and width < REFERENCE_MIN_FACE_PX):
                print(f"excluded from gallery: {v.profile_id}")
                continue
            gallery[v.profile_id] = template
            probes += [(v.profile_id, p) for p in v.probes]
        ids = list(gallery)
        G = np.stack([gallery[k] for k in ids])

        genuine, impostor, margins, rank1_errors, no_face = [], [], [], 0, 0
        for owner, path in probes:
            q, _ = embed(ms, path)
            if q is None:
                no_face += 1
                continue
            scores = G @ q
            if owner == "STRANGER":
                impostor.append(scores.max())
                continue
            mate = ids.index(owner)
            best_non_mate = np.delete(scores, mate).max()
            genuine.append(scores[mate])
            impostor.append(best_non_mate)
            margins.append(scores[mate] - best_non_mate)
            rank1_errors += int(best_non_mate > scores[mate])

    genuine, impostor, margins = map(np.array, (genuine, impostor, margins))
    proposed = math.ceil((impostor.max() + 0.02) * 100) / 100
    rows = []
    for t in (PLACEHOLDER_T, proposed):
        rows.append(f"| {t:.2f} | {(impostor >= t).sum()} of {len(impostor)} | {(genuine < t).sum()} of {len(genuine)} "
                    f"| {((genuine >= t) & (margins < MARGIN)).sum()} |")
    report = f"""# Score analysis: POC001 gallery (generated by tools/score_analysis.py)

**Mostly synthetic faces (DigiFace-1M) plus one consenting volunteer. Synthetic faces may not behave like real
ones, so this shows the method, not operational numbers. No accuracy claims.**

Model `{model_id}`; gallery of {len(ids)} enrolled references; {len(genuine)} mated probes and
{len(impostor) - len(genuine)} stranger probes ({no_face} probes skipped: not exactly one face).

| Distribution | n | min | p5 | median | p95 | max |
|---|---|---|---|---|---|---|
| Genuine (probe vs own reference) | {len(genuine)} | {stats(genuine)} |
| Impostor top-1 (best non-mate; = runner-up when the mate is enrolled) | {len(impostor)} | {stats(impostor)} |
| Margin genuine - best non-mate (mated probes) | {len(margins)} | {stats(margins)} |

Rank-1 errors (a non-mate scored higher than the mate): {rank1_errors} of {len(genuine)}.

| T | impostor top-1 >= T (false match if the mate were absent) | genuine < T (NO_MATCH, manual) | genuine >= T but margin < {MARGIN:.2f} (AMBIGUOUS) |
|---|---|---|---|
{chr(10).join(rows)}

**Proposed T = {proposed:.2f}**: the highest impostor top-1 seen ({impostor.max():.3f}) plus a 0.02 safety gap, rounded up.
With {len(impostor)} impostor searches and none above T, the rule of three gives only a rough 95% upper bound of
~{3 / len(impostor):.0%} per search for the false-match rate: far too few samples to calibrate anything. Production
sets T at a target FPIR over flight-sized galleries with the vendor's calibration and operational data.

![Genuine vs impostor histogram](img/score-histogram.png)
"""
    (ROOT / "docs" / "score-analysis.md").write_text(report, encoding="utf-8")
    histogram_png(genuine, impostor, proposed, ROOT / "docs" / "img" / "score-histogram.png")
    print(report)


if __name__ == "__main__":
    main()
