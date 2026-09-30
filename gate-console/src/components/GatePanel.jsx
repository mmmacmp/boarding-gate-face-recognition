import { useState } from 'react'
import { api, sendCapture } from '../api.js'
import DecisionBanner from './DecisionBanner.jsx'
import ScoreBars from './ScoreBars.jsx'
import Webcam from './Webcam.jsx'

// Demo captures. `display: false` = synthetic (DigiFace) face: sent to the gate, never shown on screen.
const SCENES = [
  { id: 'easy', label: 'P001 · daylight', probe: 'p001-easy', flight: 'POC001', display: true },
  { id: 'hard', label: 'P001 · night, low angle', probe: 'p001-hard', flight: 'POC001', display: true },
  { id: 'twin', label: 'Look-alike (simulated) · POC002', probe: 'p001-easy', flight: 'POC002', display: true },
  { id: 'stranger', label: 'Stranger (synthetic)', probe: 'stranger', flight: 'POC001', display: false },
]

// Synthetic passengers S001–S049 on POC001 (S035 is excluded from the gallery: reference face too small).
const SYNTHETIC = Array.from({ length: 49 }, (_, i) => `S${String(i + 1).padStart(3, '0')}`).filter((id) => id !== 'S035')

export default function GatePanel({ limits, onDecision }) {
  const [useCamera, setUseCamera] = useState(false)
  const [capture, setCapture] = useState(null) // { imageUrl | null, caption, flight }
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const [cursor, setCursor] = useState(0) // which synthetic passenger comes next

  async function send(blob, { flight, caption, display }) {
    setBusy(true)
    if (capture?.imageUrl) URL.revokeObjectURL(capture.imageUrl) // free the previous image
    setCapture({ imageUrl: display ? URL.createObjectURL(blob) : null, caption, flight })
    const event = await sendCapture(blob, flight)
    setResult(event)
    onDecision(event)
    setBusy(false)
  }

  async function playScene(scene) {
    const blob = await api.probe(scene.probe).catch(() => null)
    if (!blob) return setResult({ decision: 'MANUAL', reason: 'GATE_UNREACHABLE' })
    send(blob, { flight: scene.flight, caption: scene.label, display: scene.display })
  }

  async function nextPassenger() {
    // Synthetic passengers create traffic for the operations scenes (breaker, consent); ids and scores only.
    const id = SYNTHETIC[cursor]
    setCursor((c) => (c + 1) % SYNTHETIC.length)
    const blob = await api.probe(id).catch(() => null)
    if (blob) send(blob, { flight: 'POC001', caption: `Synthetic passenger ${id}`, display: false })
  }

  return (
    <section className="panel gate">
      <h2>Gate G1</h2>
      <div className="scene-buttons">
        {SCENES.map((s) => (
          <button key={s.id} disabled={busy} onClick={() => playScene(s)}>
            {s.label}
          </button>
        ))}
        <button disabled={busy} onClick={nextPassenger}>
          Next synthetic passenger
        </button>
        <button className={useCamera ? 'active' : ''} onClick={() => setUseCamera((on) => !on)}>
          {useCamera ? 'Webcam on' : 'Use webcam'}
        </button>
      </div>

      {useCamera && (
        <Webcam
          disabled={busy}
          onCapture={(blob) => send(blob, { flight: 'POC001', caption: 'Webcam capture', display: true })}
        />
      )}

      <div className="capture-row">
        <div className="capture">
          {capture?.imageUrl ? (
            <img src={capture.imageUrl} alt="Probe sent to the gate" />
          ) : (
            <div className="no-image">{capture ? 'Synthetic face: image not shown' : 'No capture yet'}</div>
          )}
          {capture && (
            <div className="caption">
              {capture.caption} · flight {capture.flight}
            </div>
          )}
        </div>
        <div className="result">
          <DecisionBanner event={result} busy={busy} />
          <ScoreBars event={result} limits={limits} />
        </div>
      </div>
    </section>
  )
}
