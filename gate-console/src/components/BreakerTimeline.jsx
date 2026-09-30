const WINDOW_MS = 3 * 60 * 1000 // show the last 3 minutes

// Resilience4j writes times like "2026-09-30T15:10:00.123+03:00[Asia/Riyadh]"; drop the zone name to parse it.
const parseTime = (t) => Date.parse(String(t).replace(/\[.*\]$/, ''))

/**
 * One horizontal strip per breaker: coloured segments for CLOSED / OPEN / HALF_OPEN over the last 3 minutes.
 * Built from the breaker's own STATE_TRANSITION events (/actuator/circuitbreakerevents), so no change is missed
 * between two polls.
 */
export default function BreakerTimeline({ name, state, events }) {
  const now = Date.now()
  const start = now - WINDOW_MS
  const transitions = events
    .filter((e) => e.circuitBreakerName === name && e.type === 'STATE_TRANSITION')
    .map((e) => {
      // Resilience4j writes e.g. "State transition from CLOSED to OPEN"
      const [, from, to] = e.stateTransition.match(/from (\w+) to (\w+)/) ?? []
      return { at: parseTime(e.creationTime), from, to }
    })
    .filter((t) => t.to && !Number.isNaN(t.at))
    .sort((a, b) => a.at - b.at)

  // State at the left edge: the last change before the window, else the "from" of the first change in it.
  const before = transitions.filter((t) => t.at < start).at(-1)
  const inside = transitions.filter((t) => t.at >= start)
  let current = before?.to ?? inside[0]?.from ?? state ?? 'CLOSED'
  let from = start
  const segments = []
  for (const t of inside) {
    segments.push({ state: current, from, to: t.at })
    current = t.to
    from = t.at
  }
  segments.push({ state: current, from, to: now })

  const time = (ms) => new Date(ms).toLocaleTimeString()
  return (
    <div className="breaker">
      <div className="breaker-head">
        <span>{name === 'identity' ? 'Identity provider' : 'Model service'}</span>
        <span className={`state-chip ${state ?? 'UNKNOWN'}`}>{state ?? 'unknown'}</span>
      </div>
      <div className="timeline">
        {segments.map((s, i) => (
          <div
            key={i}
            className={`segment ${s.state}`}
            style={{ width: `${((s.to - s.from) / WINDOW_MS) * 100}%` }}
            title={`${s.state} ${time(s.from)} – ${time(s.to)}`}
          />
        ))}
      </div>
      <div className="timeline-axis">
        <span>−3 min</span>
        <span>now</span>
      </div>
    </div>
  )
}
