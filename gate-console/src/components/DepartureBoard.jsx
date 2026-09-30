import { reducedMotion } from '../useFlip.js'

/** One character cell. Its `key` includes the character, so a changed character re-mounts and plays the flip. */
function Flap({ char, delay }) {
  return (
    <span className="flap" style={{ animationDelay: reducedMotion() ? '0ms' : `${delay}ms` }}>
      {char === ' ' ? ' ' : char}
    </span>
  )
}

/** Split-flap text: fixed width, one cell per character, a small stagger from left to right. */
function SplitFlap({ text, width }) {
  const chars = String(text).toUpperCase().padEnd(width).slice(0, width).split('')
  return (
    <span className="splitflap" aria-label={text}>
      {chars.map((c, i) => <Flap key={`${i}-${c}`} char={c} delay={i * 35} />)}
    </span>
  )
}

const STATUS = { BUILDING: 'PREPARING', BOARDING: 'BOARDING', DEPARTED: 'GATE CLOSED', PURGED: 'GATE CLOSED' }

/** The header strip: a fictional departure board for the demo flight. */
export default function DepartureBoard({ flight }) {
  const status = STATUS[flight?.status] ?? 'PREPARING'
  const boarding = flight?.departure_time
    ? new Date(new Date(flight.departure_time).getTime() - 40 * 60 * 1000).toTimeString().slice(0, 5)
    : '--:--'
  const closed = status === 'GATE CLOSED'
  return (
    <div className="board">
      <div className="board-cell"><span className="board-label">Flight</span><SplitFlap text="DEMO01" width={6} /></div>
      <div className="board-cell wide">
        <span className="board-label">Destination <span lang="ar" dir="rtl">الوجهة</span></span>
        <SplitFlap text="AURORA CITY" width={11} />
      </div>
      <div className="board-cell"><span className="board-label">Gate</span><SplitFlap text="G1" width={2} /></div>
      <div className="board-cell"><span className="board-label">Boarding</span><SplitFlap text={boarding} width={5} /></div>
      <div className={`board-cell status ${closed ? 'closed' : 'open'}`}>
        <span className="board-label">Status <span lang="ar" dir="rtl">الحالة</span></span>
        <SplitFlap text={status} width={11} />
      </div>
    </div>
  )
}
