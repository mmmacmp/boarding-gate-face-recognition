// Stage timings returned by the gate-service with each decision, drawn as a waterfall: each bar starts where the
// previous one ended, so the right edge of the last bar is the total time spent in the gate-service.
const STAGES = [
  ['embed_ms', 'Embed (model-service)'],
  ['search_ms', 'Decrypt + search'],
  ['identity_ms', 'Identity check'],
  ['db_ms', 'DB write'],
]

export default function LatencyWaterfall({ timings }) {
  if (!timings) return <p className="muted">Send a capture to see where the time goes.</p>
  const total = STAGES.reduce((sum, [key]) => sum + (timings[key] ?? 0), 0)
  const scale = Math.max(total, 100) // at least 100 ms wide, so tiny totals don't look huge
  let offset = 0
  return (
    <div className="waterfall">
      {STAGES.map(([key, label], i) => {
        const ms = timings[key]
        const left = (offset / scale) * 100
        offset += ms ?? 0
        return (
          <div className="wf-row" key={key}>
            <span className="wf-label">{label}</span>
            <div className="wf-track">
              {ms != null && (
                <div className={`wf-bar s${i + 1}`} title={`${label}: ${ms} ms`}
                     style={{ left: `${left}%`, width: `${Math.max((ms / scale) * 100, 0.5)}%` }} />
              )}
            </div>
            <span className="wf-ms">{ms == null ? 'skipped' : `${ms.toFixed(1)} ms`}</span>
          </div>
        )
      })}
      <div className="wf-total">Total in gate-service: {total.toFixed(1)} ms</div>
    </div>
  )
}
