import { api } from '../api.js'
import { usePoll } from '../usePoll.js'
import BreakerTimeline from './BreakerTimeline.jsx'
import LatencyWaterfall from './LatencyWaterfall.jsx'

// Loader outside the component so its identity never changes (usePoll restarts when `load` changes).
const loadBreakers = () =>
  Promise.all([api.breakers(), api.breakerEvents()]).then(([b, e]) => ({
    states: b.circuitBreakers,
    events: e.circuitBreakerEvents,
  }))

/** Operations view: breaker lights with their timelines, and where the time went for the last decision. */
export default function OpsPanel({ lastEvent, breakerSettings }) {
  const { data } = usePoll(loadBreakers, 1000)
  return (
    <section className="card ops-card">
      <div className="ops-col">
        <header className="card-head">Circuit breakers</header>
        <BreakerTimeline name="identity" label="Identity provider" state={data?.states?.identity?.state} events={data?.events ?? []} />
        <BreakerTimeline name="model" label="Face service" state={data?.states?.model?.state} events={data?.events ?? []} />
        {breakerSettings && (
          <p className="demo-setting">
            Demo setting: the identity breaker opens after {breakerSettings.minimum_calls} calls with ≥ 50% failures
            (window {breakerSettings.window_size}), {breakerSettings.half_open_calls} trial call. Tested default: window 10, minimum 5.
          </p>
        )}
      </div>
      <div className="ops-col">
        <header className="card-head">Last decision · latency by stage</header>
        <LatencyWaterfall timings={lastEvent?.timings} eventKey={lastEvent?.event_id} />
      </div>
    </section>
  )
}
