import { AlertIcon, CheckIcon, SearchIcon } from './Icons.jsx'

const WINDOW_MS = 2 * 60 * 1000 // show the last 2 minutes

// Resilience4j writes times like "2026-09-30T15:10:00.123+03:00[Asia/Riyadh]"; drop the zone name to parse it.
const parseTime = (t) => Date.parse(String(t).replace(/\[.*\]$/, ''))

const LIGHT = {
  CLOSED: ['Closed · calls flow', CheckIcon],
  OPEN: ['Open · instant manual', AlertIcon],
  HALF_OPEN: ['Half-open · trial call', SearchIcon],
}

/**
 * A glowing status light plus a strip of the breaker's states over the last 2 minutes, built from its own
 * STATE_TRANSITION events (/actuator/circuitbreakerevents), so no change is missed between two polls.
 */
export default function BreakerTimeline({ name, label, state, events }) {
  const now = Date.now()
  // A demo reset calls CircuitBreaker.reset() (a RESET event): history before it belongs to the previous rehearsal.
  const lastReset = Math.max(0, ...events
    .filter((e) => e.circuitBreakerName === name && e.type === 'RESET').map((e) => parseTime(e.creationTime)))
  const start = Math.max(now - WINDOW_MS, lastReset)
  const transitions = events
    .filter((e) => e.circuitBreakerName === name && e.type === 'STATE_TRANSITION' && parseTime(e.creationTime) >= lastReset)
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
  const segments = start > now - WINDOW_MS ? [{ state: 'NONE', from: now - WINDOW_MS, to: start }] : [] // before the reset
  for (const t of inside) {
    segments.push({ state: current, from, to: t.at })
    current = t.to
    from = t.at
  }
  segments.push({ state: current, from, to: now })

  const [text, Icon] = LIGHT[state] ?? ['Unknown', AlertIcon]
  const time = (ms) => new Date(ms).toLocaleTimeString()
  return (
    <div className="breaker">
      <div className="breaker-head">
        <span className={`light light-${state ?? 'UNKNOWN'}`} aria-hidden="true" />
        <span className="breaker-name">{label}</span>
        <span className={`breaker-state state-${state ?? 'UNKNOWN'}`}><Icon size={14} /> {text}</span>
      </div>
      <div className="timeline">
        {segments.map((s, i) => (
          <div key={i} className={`segment ${s.state}`} style={{ width: `${((s.to - s.from) / WINDOW_MS) * 100}%` }}
               title={`${s.state} ${time(s.from)} – ${time(s.to)}`} />
        ))}
      </div>
      <div className="timeline-axis"><span>−2 min</span><span>now</span></div>
    </div>
  )
}
