import { useEffect, useState } from 'react'
import { api } from '../api.js'
import CountUp from './CountUp.jsx'

/** Crop the detected face out of the probe image in the browser (for display only; nothing is uploaded). */
function useFaceCrop(url, box) {
  const [crop, setCrop] = useState(null)
  useEffect(() => {
    setCrop(null)
    if (!url || !box) return
    const img = new Image()
    img.onload = () => {
      const [x1, y1, x2, y2] = box
      const side = Math.min(Math.max(x2 - x1, y2 - y1) * 1.6, img.width, img.height)
      const left = Math.min(Math.max((x1 + x2) / 2 - side / 2, 0), img.width - side)
      const top = Math.min(Math.max((y1 + y2) / 2 - side / 2, 0), img.height - side)
      const canvas = document.createElement('canvas')
      canvas.width = canvas.height = 240
      canvas.getContext('2d').drawImage(img, left, top, side, side, 0, 0, 240, 240)
      setCrop(canvas.toDataURL('image/jpeg', 0.9))
    }
    img.src = url
  }, [url, box])
  return crop
}

/** Half-circle gauge 0..1 with the threshold as a tick. */
function Gauge({ score, threshold }) {
  const angle = (v) => Math.PI * (1 - Math.max(0, Math.min(v, 1)))
  const point = (v, r) => [60 + r * Math.cos(angle(v)), 60 - r * Math.sin(angle(v))]
  const [ex, ey] = point(score, 48)
  const [t1x, t1y] = point(threshold, 40)
  const [t2x, t2y] = point(threshold, 58)
  return (
    <svg className="gauge" viewBox="0 0 120 70" aria-hidden="true">
      <path d="M12 60 A48 48 0 0 1 108 60" className="gauge-bg" />
      <path d={`M12 60 A48 48 0 0 1 ${ex} ${ey}`} className={`gauge-fill ${score >= threshold ? 'above' : 'below'}`} />
      <line x1={t1x} y1={t1y} x2={t2x} y2={t2y} className="gauge-t" />
    </svg>
  )
}

/**
 * Probe face next to the top-1 reference, with the score gauge and the margin to top-2.
 * For webcam captures it also shows P001's live score against the threshold, so the presenter sees how close it is.
 */
export default function ComparisonCard({ event, capture, limits, explanation }) {
  const ranked = Object.entries(event?.xray?.scores ?? {})
  const [top1, top2] = ranked
  const crop = useFaceCrop(capture?.url, event?.xray?.face_boxes?.[0])
  const live = capture?.webcam ? event?.xray?.scores?.P001 : undefined
  return (
    <section className="card compare-card">
      <header className="card-head">Comparison <span className="muted">probe vs best match</span></header>
      {top1 ? (
        <div className="compare-row" key={event.event_id}>
          <figure><img src={crop ?? capture?.url} alt="Probe face" /><figcaption>Gate capture</figcaption></figure>
          <div className="compare-mid">
            <Gauge score={top1[1]} threshold={limits.threshold} />
            <div className="compare-score"><CountUp value={top1[1]} /></div>
            <div className="compare-t">T = {limits.threshold}</div>
            <div className="compare-margin">
              {top2 ? <>margin to {top2[0]}: <strong><CountUp value={top1[1] - top2[1]} /></strong> (Δ = {limits.margin})</>
                : 'no second candidate'}
            </div>
          </div>
          <figure><img src={api.thumbnailUrl(top1[0])} alt={`Reference ${top1[0]}`} /><figcaption>Reference {top1[0]}</figcaption></figure>
        </div>
      ) : <div className="compare-empty">No comparison for this event (nothing was searched).</div>}
      {capture?.webcam && (
        <div className={`live-score ${live != null && live >= limits.threshold ? 'above' : 'below'}`}>
          Live score vs P001: <strong>{live != null ? live.toFixed(3) : 'not searched'}</strong>
          {live != null && <> ({live >= limits.threshold ? '+' : ''}{(live - limits.threshold).toFixed(3)} vs T {limits.threshold})</>}
        </div>
      )}
      {explanation && <p className="explain">{explanation}</p>}
    </section>
  )
}
