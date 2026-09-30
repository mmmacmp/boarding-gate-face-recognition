import { useState } from 'react'
import { api } from '../api.js'
import { usePoll } from '../usePoll.js'
import BreakerTimeline from './BreakerTimeline.jsx'
import LatencyWaterfall from './LatencyWaterfall.jsx'
import RecentEvents from './RecentEvents.jsx'

// Loaders live outside the component so their identity never changes (usePoll restarts when `load` changes).
const loadStatus = () =>
  Promise.all([
    api.flight('POC001').catch(() => null), // 404 right after a reset: the flight does not exist yet
    api.flight('POC002').catch(() => null),
    api.breakers(),
    api.breakerEvents(),
    api.recentEvents(),
  ]).then(([f1, f2, breakers, breakerEvents, events]) => ({
    flights: [f1, f2].filter(Boolean),
    breakers: breakers.circuitBreakers,
    breakerEvents: breakerEvents.circuitBreakerEvents,
    events,
  }))

const loadFaults = () => Promise.all([api.idpFaults(), api.modelFaults()]).then(([idp, model]) => ({ idp, model }))

const FAULTS = [
  { id: 'idp-slow', label: 'Slow IDP (2 s)', apply: () => api.setIdpFaults({ latency_ms: 2000 }), on: (f) => f.idp.latency_ms > 0 },
  { id: 'idp-down', label: 'IDP down', apply: () => api.setIdpFaults({ down: true }), on: (f) => f.idp.down },
  { id: 'model-down', label: 'Model down', apply: () => api.setModelFaults({ down: true }), on: (f) => f.model.down },
]

export default function OpsPanel({ lastEvent }) {
  const status = usePoll(loadStatus, 1000)
  const faults = usePoll(loadFaults, 1000)
  const [profileId, setProfileId] = useState('S002')
  const [message, setMessage] = useState('')

  const run = (label, action) =>
    action()
      .then((r) => setMessage(`${label}: ${r?.output ?? 'done'}`))
      .catch((e) => setMessage(`${label} failed: ${e.message}`))

  const data = status.data
  const events = data?.events ?? []
  const manual = events.filter((e) => e.decision === 'MANUAL').length

  return (
    <section className="panel ops">
      <h2>Operations</h2>
      {status.error && <p className="notice">gate-service not reachable ({status.error})</p>}

      <div className="tiles">
        {data?.flights.map((f) => (
          <div className="tile" key={f.flight_id}>
            <div className="tile-label">{f.flight_id} · {f.status}</div>
            <div className="tile-value">{f.active_entries} <span>matchable</span></div>
            <div className="tile-sub">{f.boarded_entries} boarded</div>
          </div>
        ))}
        <div className="tile">
          <div className="tile-label">Manual fallback</div>
          <div className="tile-value">{events.length ? `${Math.round((manual / events.length) * 100)}%` : '—'}</div>
          <div className="tile-sub">{manual} of last {events.length} events</div>
        </div>
      </div>

      <h3>Circuit breakers</h3>
      {['identity', 'model'].map((name) => (
        <BreakerTimeline
          key={name}
          name={name}
          state={data?.breakers?.[name]?.state}
          events={data?.breakerEvents ?? []}
        />
      ))}

      <h3>Last decision: latency by stage</h3>
      <LatencyWaterfall timings={lastEvent?.timings} />

      <h3>Controls</h3>
      <div className="controls">
        {FAULTS.map((f) => (
          <button key={f.id} className={faults.data && f.on(faults.data) ? 'active fault' : ''}
                  onClick={() => run(f.label, f.apply)}>
            {f.label}
          </button>
        ))}
        <button onClick={() => run('Clear faults', () => Promise.all([api.setIdpFaults({}), api.setModelFaults({})]))}>
          Clear faults
        </button>
      </div>
      <div className="controls">
        <input value={profileId} onChange={(e) => setProfileId(e.target.value.trim())} aria-label="Profile id" />
        <button onClick={() => run(`Withdraw consent ${profileId}`, () => api.withdrawConsent(profileId).then(
          (w) => ({ output: `${w.status}, ${w.removed_entries} template removed` })))}>
          Withdraw consent
        </button>
        <button className="danger" onClick={() => { setMessage('Resetting demo (~20 s; captures sent meanwhile get GALLERY_UNAVAILABLE)…'); run('Reset demo', api.resetDemo) }}>
          Reset demo
        </button>
      </div>
      {message && <p className="message">{message}</p>}

      <h3>Recent events</h3>
      <RecentEvents events={events} />
    </section>
  )
}
