/** Top-1 score against the threshold T, and the top-1 minus top-2 margin against Δ. */
function Bar({ label, value, limit, limitLabel, max }) {
  const pct = (v) => `${Math.max(0, Math.min(v / max, 1)) * 100}%`
  const passes = value != null && value >= limit
  return (
    <div className="bar-row">
      <div className="bar-label">
        <span>{label}</span>
        <span className="bar-value">{value == null ? 'no score' : value.toFixed(3)}</span>
      </div>
      <div className="bar-track" title={`${label}: ${value?.toFixed(3) ?? '—'} (${limitLabel})`}>
        {value != null && <div className={`bar-fill ${passes ? 'pass' : 'fail'}`} style={{ width: pct(value) }} />}
        <div className="bar-limit" style={{ left: pct(limit) }} />
      </div>
      <div className="bar-limit-label">{limitLabel}</div>
    </div>
  )
}

export default function ScoreBars({ event, limits }) {
  if (!limits) return <p className="muted">Thresholds unavailable (gate-service or model-service not reachable).</p>
  const top1 = event?.top1 ?? null
  const margin = top1 != null && event?.top2 != null ? top1 - event.top2 : null
  return (
    <div className="scores">
      <Bar label="Top-1 score" value={top1} limit={limits.threshold} limitLabel={`T = ${limits.threshold}`} max={1} />
      <Bar label="Margin (top-1 − top-2)" value={margin} limit={limits.margin} limitLabel={`Δ = ${limits.margin}`} max={0.6} />
      <p className="muted small">Thresholds are illustrative, not calibrated.</p>
    </div>
  )
}
