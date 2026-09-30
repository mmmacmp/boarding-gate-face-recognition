import { useCallback, useEffect, useRef, useState } from 'react'
import { api, sendCapture } from './api.js'
import ComparisonCard from './components/ComparisonCard.jsx'
import DecisionBanner from './components/DecisionBanner.jsx'
import DepartureBoard from './components/DepartureBoard.jsx'
import GalleryBoard from './components/GalleryBoard.jsx'
import GateCapture from './components/GateCapture.jsx'
import OpsPanel from './components/OpsPanel.jsx'
import PresenterPanel from './components/PresenterPanel.jsx'
import { FLIGHT, PROBES, STEPS } from './script.js'
import { explain } from './text.js'
import { reducedMotion } from './useFlip.js'

const wait = (ms) => new Promise((r) => setTimeout(r, ms))
const pause = (ms) => wait(reducedMotion() ? Math.min(ms, 60) : ms) // visual pacing only
const NO_SCAN = { scores: null, current: null, revealed: false, top1: null, top2: null }
const waitingCount = (g) => g?.entries.filter((e) => e.status === 'ACTIVE').length

export default function App() {
  const [limits, setLimits] = useState(null)
  const [flight, setFlight] = useState(null)
  const [gallery, setGallery] = useState(null)
  const [previousWaiting, setPreviousWaiting] = useState(null)
  const [capture, setCapture] = useState(null) // { url, label, webcam }
  const [scanning, setScanning] = useState(false)
  const [scan, setScan] = useState(NO_SCAN)
  const [event, setEvent] = useState(null)
  const [replayOf, setReplayOf] = useState(null)
  const [busy, setBusy] = useState(false)
  const [pointer, setPointer] = useState(0)
  const [results, setResults] = useState([])
  const [source, setSource] = useState('stored')
  const [liveView, setLiveView] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [notice, setNotice] = useState('')
  const cameraRef = useRef(null)
  const galleryRef = useRef(null)
  const lastBoard = useRef(null) // { blob, key, eventId, label } of the last BOARD, for the replay step
  const playingRef = useRef(false)

  useEffect(() => {
    Promise.all([api.policy(), api.modelHealth()])
      .then(([p, m]) => setLimits({ threshold: p.thresholds[m.model_id], margin: p.margin }))
      .catch(() => setNotice('gate-service or model-service not reachable'))
  }, [])

  // The database is the only source of truth for where tiles are.
  const refresh = useCallback(async () => {
    const [f, g] = await Promise.all([api.flight(FLIGHT).catch(() => null), api.demoGallery(FLIGHT).catch(() => null)])
    setFlight(f)
    if (g) {
      if (galleryRef.current && waitingCount(galleryRef.current) !== waitingCount(g)) {
        setPreviousWaiting(waitingCount(galleryRef.current))
      }
      galleryRef.current = g
      setGallery(g)
    }
  }, [])
  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, 3000)
    return () => clearInterval(timer)
  }, [refresh])

  // Show the live preview for webcam steps (P001 scenes) when the presenter chose "Live webcam".
  const step = STEPS[pointer]
  useEffect(() => setLiveView(Boolean(step?.webcam && source === 'webcam')), [step, source])

  async function captureFromCamera() {
    setLiveView(true)
    for (let i = 0; i < 40 && !cameraRef.current; i++) await wait(100) // camera component mounting
    await wait(600)
    const blob = await cameraRef.current.capture()
    setLiveView(false)
    return blob
  }

  /** One gate event: sweep, send, scan the searched tiles, reveal, then re-read the gallery from the server. */
  async function runProbe(probe, { webcam = false, replay = false } = {}) {
    setBusy(true)
    setEvent(null)
    setReplayOf(null)
    setScan(NO_SCAN)
    let blob, label, key, replayId = null
    if (replay) {
      if (!lastBoard.current) {
        setNotice('Nothing to replay yet: board a passenger first.')
        setBusy(false)
        return null
      }
      ({ blob, key } = lastBoard.current)
      label = `${lastBoard.current.label} · resent with the same Idempotency-Key`
      replayId = lastBoard.current.eventId
    } else if (webcam) {
      blob = await captureFromCamera()
      label = 'Live webcam capture'
    } else {
      blob = await api.probe(probe)
      label = PROBES[probe].label
    }
    setCapture({ url: URL.createObjectURL(blob), label, webcam })
    setScanning(true)
    const [ev] = await Promise.all([sendCapture(blob, FLIGHT, key), pause(900)])
    setScanning(false)

    const scores = ev.xray?.scores ?? {}
    const ranked = Object.keys(scores) // best first
    const order = (galleryRef.current?.entries ?? []).map((e) => e.profile_id).filter((id) => id in scores)
    for (const id of order) {
      setScan({ ...NO_SCAN, scores, current: id })
      await pause(170)
    }
    setScan({ scores, current: null, revealed: true, top1: ranked[0] ?? null, top2: ranked[1] ?? null })
    await pause(order.length ? 250 : 0)
    setEvent(ev)
    setReplayOf(replayId)
    if (ev.reason === 'MATCHED' && !replay) lastBoard.current = { blob, key: ev.key, eventId: ev.event_id, label }
    await pause(700) // let the decision land, then show the database's new state
    await refresh()
    setBusy(false)
    return { ev, replayId }
  }

  async function runAction(name, who) {
    setBusy(true)
    const clear = () => Promise.all([api.setIdpFaults({}), api.setModelFaults({})])
    try {
      if (name === 'withdrawTwin') await api.withdrawConsent('P001-TWIN')
      if (name === 'idpInvalidS01') await api.setIdpStatus('S01', 'INVALID')
      if (name === 'idpSlow') await api.setIdpFaults({ latency_ms: 2000 })
      if (name === 'idpDown') await api.setIdpFaults({ down: true })
      if (name === 'modelDown') await api.setModelFaults({ down: true })
      if (name === 'clear') await clear()
      if (name === 'withdraw') await api.withdrawConsent(who)
      if (name === 'consent') await api.grantConsent(who)
      if (name === 'invalid' || name === 'valid') await api.setIdpStatus(who, name.toUpperCase())
      if (name === 'recover') {
        await clear()
        for (let n = 6; n > 0; n--) {
          setCooldown(n)
          await wait(1000) // real time: the breaker's open state lasts 5 s in the demo profile
        }
        setCooldown(0)
      }
      if (name === 'depart') {
        await clear()
        await api.depart(FLIGHT)
        for (let i = 0; i < 30 && (await api.flight(FLIGHT)).status !== 'PURGED'; i++) await wait(1000)
      }
      setNotice('')
    } catch (e) {
      setNotice(`${name} failed: ${e.message}`)
    }
    await refresh()
    setBusy(false)
  }

  async function runStep(index, { stored = false } = {}) {
    const s = STEPS[index]
    if (s.action) {
      await runAction(s.action)
      setPointer(index + 1)
      return true
    }
    const out = await runProbe(s.probe, { webcam: !stored && s.webcam && source === 'webcam', replay: s.replay })
    if (!out) return false
    const { ev, replayId } = out
    const replayed = s.replay ? ev.event_id === replayId : true
    const ok = ev.reason === s.expect && replayed
    const n = STEPS.slice(0, index + 1).filter((x) => !x.action).length
    setResults((r) => [...r, {
      index, ok, label: `#${n} ${s.title}`, expect: s.expect,
      actual: s.replay ? `${ev.reason} ${replayed ? '(same event)' : '(NEW event)'}` : ev.reason,
    }])
    if (ok) setPointer(index + 1) // on a mismatch stay here: retry, switch source, or skip
    return ok
  }

  async function playAll() {
    if (playingRef.current) {
      playingRef.current = false // takes effect after the current step
      return
    }
    playingRef.current = true
    setPlaying(true)
    for (let i = pointer; playingRef.current && i < STEPS.length; i++) {
      const ok = await runStep(i, { stored: true })
      if (!ok) break // stop on a mismatch so it gets looked at
      await wait(STEPS[i].action ? 800 : 2200)
    }
    playingRef.current = false
    setPlaying(false)
  }

  async function reset() {
    setResetting(true)
    setNotice('Resetting DEMO01 (about 10 s)…')
    try {
      await api.resetDemoFlight()
      setNotice('DEMO01 reset: 5 passengers waiting, faults cleared, breakers closed.')
    } catch (e) {
      setNotice(`Reset failed: ${e.message}`)
    }
    lastBoard.current = null
    setPointer(0)
    setResults([])
    setEvent(null)
    setCapture(null)
    setScan(NO_SCAN)
    setPreviousWaiting(null)
    galleryRef.current = null
    await refresh()
    setResetting(false)
  }

  const freeProbe = (name) => (name === 'webcam' ? runProbe(null, { webcam: true }) : runProbe(name))
  const explanation = event && limits ? explain(event, limits, { replayOf }) : ''

  return (
    <div className="app">
      <DepartureBoard flight={flight} />
      <div className="disclaimer" role="note">
        <strong>Demo view: shows system internals. A production gate would never display this.</strong>
        <span>Demo flight trimmed to 5 passengers for visibility; threshold calibrated on a 49-passenger gallery.</span>
      </div>
      <main className="stage">
        <div className="col-gate">
          <GateCapture capture={capture} scanning={scanning} faceBoxes={event?.xray?.face_boxes ?? []}
                       webcam={liveView} cameraRef={cameraRef} />
          <DecisionBanner event={event} busy={busy && !cooldown} replayOf={replayOf} />
        </div>
        <div className="col-gallery">
          <GalleryBoard gallery={gallery} previousWaiting={previousWaiting}
                        scan={{ ...scan, threshold: limits?.threshold ?? 0.44 }} />
          {limits && <ComparisonCard event={event} capture={capture} limits={limits} explanation={explanation} />}
        </div>
        <PresenterPanel steps={STEPS} pointer={pointer} results={results} busy={busy} cooldown={cooldown}
                        source={source} setSource={setSource} onRun={() => runStep(pointer)}
                        onSkip={() => setPointer((p) => p + 1)} playing={playing} onPlayAll={playAll}
                        onReset={reset} resetting={resetting} onFreeProbe={freeProbe} onFreeAction={runAction} />
        <OpsPanel lastEvent={event} breakerSettings={gallery?.identity_breaker} />
      </main>
      {notice && <div className="toast" role="status">{notice}</div>}
      <footer className="footer">
        <span>Prototype by Mahmoud · 2026</span>
        <span>Synthetic faces: DigiFace-1M, Microsoft, research licence (R-UDA v1.0, non-commercial)</span>
        <span>Interview POC · not production-ready · all external systems are mocks</span>
      </footer>
    </div>
  )
}
