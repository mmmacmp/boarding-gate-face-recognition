import { useState } from 'react'
import { PROBES } from '../script.js'
import { BoltIcon, CameraIcon, CheckIcon, CrossIcon, PauseIcon, PlayIcon, ResetIcon } from './Icons.jsx'

const probeNumber = (steps, index) => steps.slice(0, index + 1).filter((s) => !s.action).length

function StepCard({ step, index, steps, source, setSource, mismatch }) {
  if (!step) return <div className="step-card done"><CheckIcon size={20} /> Script complete. Reset to run it again.</div>
  return (
    <div className={`step-card ${step.action ? 'is-action' : 'is-probe'}`}>
      <div className="step-kind">
        {step.action ? <><BoltIcon size={16} /> Action (your click)</> : <>Probe {probeNumber(steps, index)} of 15 · case {step.case}{step.cases ? ` + ${step.cases.join(', ')}` : ''}</>}
      </div>
      <div className="step-title">{step.title}</div>
      <p className="step-say">{step.say}</p>
      {!step.action && <div className="step-expect">Expected: <span className="code">{step.expect}</span></div>}
      {step.webcam && (
        <div className="source-toggle" role="group" aria-label="Capture source">
          <button className={source === 'stored' ? 'on' : ''} onClick={() => setSource('stored')}>Stored photo</button>
          <button className={source === 'webcam' ? 'on' : ''} onClick={() => setSource('webcam')}><CameraIcon size={16} /> Live webcam</button>
        </div>
      )}
      {mismatch && (
        <div className="mismatch"><CrossIcon size={16} /> Expected {mismatch.expect}, got {mismatch.actual}. Retry, switch to the stored photo, or skip.</div>
      )}
    </div>
  )
}

function FreeMode({ onProbe, onAction, disabled }) {
  const [who, setWho] = useState('P001-TWIN')
  const people = ['P001', 'P001-TWIN', 'S01', 'S02', 'S03']
  return (
    <div className="free">
      <div className="free-group">Send a capture</div>
      <div className="free-grid">
        {Object.entries(PROBES).map(([name, p]) => (
          <button key={name} disabled={disabled} onClick={() => onProbe(name)}>{p.label}</button>
        ))}
        <button disabled={disabled} onClick={() => onProbe('webcam')}><CameraIcon size={16} /> Webcam capture</button>
      </div>
      <div className="free-group">Passenger actions</div>
      <div className="free-row">
        <select value={who} onChange={(e) => setWho(e.target.value)} aria-label="Passenger">
          {people.map((p) => <option key={p}>{p}</option>)}
        </select>
        <button disabled={disabled} onClick={() => onAction('withdraw', who)}>Withdraw consent</button>
        <button disabled={disabled} onClick={() => onAction('consent', who)}>Give consent</button>
        <button disabled={disabled} onClick={() => onAction('invalid', who)}>IdP: invalid</button>
        <button disabled={disabled} onClick={() => onAction('valid', who)}>IdP: valid</button>
      </div>
      <div className="free-group">Faults and flight</div>
      <div className="free-row">
        <button disabled={disabled} onClick={() => onAction('idpSlow')}>IdP slow (2 s)</button>
        <button disabled={disabled} onClick={() => onAction('idpDown')}>IdP down</button>
        <button disabled={disabled} onClick={() => onAction('modelDown')}>Model down</button>
        <button disabled={disabled} onClick={() => onAction('clear')}>Clear faults</button>
        <button disabled={disabled} onClick={() => onAction('depart')}>Depart</button>
      </div>
    </div>
  )
}

/**
 * The presenter's cockpit: the next scripted step and a button to run it (nothing runs by itself), the results so far
 * (expected vs actual, mismatches highlighted), "Play all" for quick verification, and a free mode.
 */
export default function PresenterPanel(props) {
  const { steps, pointer, results, busy, cooldown, source, setSource, onRun, onSkip, playing, onPlayAll, onReset,
          resetting, onFreeProbe, onFreeAction } = props
  const [free, setFree] = useState(false)
  const step = steps[pointer]
  const last = results.at(-1)
  const mismatch = last && last.index === pointer && !last.ok ? last : null
  const locked = busy || resetting || playing || cooldown > 0
  return (
    <aside className="card presenter">
      <header className="card-head">Presenter
        <div className="tabs">
          <button className={!free ? 'on' : ''} onClick={() => setFree(false)}>Script</button>
          <button className={free ? 'on' : ''} onClick={() => setFree(true)}>Free mode</button>
        </div>
      </header>
      {free ? <FreeMode onProbe={onFreeProbe} onAction={onFreeAction} disabled={busy || resetting || playing} /> : (
        <>
          <div className="progress"><div style={{ width: `${(pointer / steps.length) * 100}%` }} /></div>
          <div className="progress-label">Step {Math.min(pointer + 1, steps.length)} of {steps.length}</div>
          <StepCard step={step} index={pointer} steps={steps} source={source} setSource={setSource} mismatch={mismatch} />
          <div className="run-row">
            <button className="primary" disabled={!step || locked} onClick={onRun}>
              {cooldown > 0 ? `Cooling down… ${cooldown}` : step?.action ? <><BoltIcon size={18} /> Do it</> : <><PlayIcon size={18} /> Send capture</>}
            </button>
            <button disabled={!step || locked} onClick={onSkip}>Skip</button>
          </div>
        </>
      )}
      <div className="results">
        <div className="results-head">Expected vs actual</div>
        <ol>
          {results.map((r, i) => (
            <li key={i} className={r.ok ? 'ok' : 'bad'}>
              {r.ok ? <CheckIcon size={16} /> : <CrossIcon size={16} />}
              <span className="r-n">{r.label}</span>
              <span className="r-exp">{r.expect}</span>
              <span className="r-act">{r.actual}</span>
            </li>
          ))}
        </ol>
      </div>
      <div className="util-row">
        <button onClick={onPlayAll} disabled={busy || resetting || (!playing && !step)}>
          {playing ? <><PauseIcon size={16} /> Pause</> : <><PlayIcon size={16} /> Play all (verification)</>}
        </button>
        <button className="danger" onClick={onReset} disabled={busy || resetting || playing}>
          <ResetIcon size={16} /> {resetting ? 'Resetting…' : 'Reset demo'}
        </button>
      </div>
    </aside>
  )
}
