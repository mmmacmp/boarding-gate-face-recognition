import { useEffect, useState } from 'react'
import { api } from './api.js'
import GatePanel from './components/GatePanel.jsx'
import OpsPanel from './components/OpsPanel.jsx'

/** Page layout: the gate (what a passenger sees) on the left, operations (what an agent sees) on the right. */
export default function App() {
  const [lastEvent, setLastEvent] = useState(null) // newest decision made from this console (has stage timings)
  const [limits, setLimits] = useState(null) // { threshold, margin } for the model that is running

  useEffect(() => {
    Promise.all([api.policy(), api.modelHealth()])
      .then(([policy, model]) => setLimits({ threshold: policy.thresholds[model.model_id], margin: policy.margin }))
      .catch(() => setLimits(null))
  }, [])

  return (
    <div className="page">
      <header className="topbar">
        <span className="brand">POC boarding gate · demo console</span>
        <span className="warning-tag">Interview POC · not production-ready · all external systems are mocks</span>
      </header>
      <main className="columns">
        <GatePanel limits={limits} onDecision={setLastEvent} />
        <OpsPanel lastEvent={lastEvent} />
      </main>
    </div>
  )
}
