import { api } from '../api.js'
import { useFlip } from '../useFlip.js'
import CountUp from './CountUp.jsx'
import { BanIcon, CheckIcon, LockIcon, SearchIcon } from './Icons.jsx'

const AREAS = [
  { id: 'waiting', title: 'Waiting to board', ar: 'بانتظار الصعود', note: 'the only ones searched', icon: SearchIcon },
  { id: 'boarded', title: 'Boarded', ar: 'صعدوا', note: 'no longer searched', icon: CheckIcon },
  { id: 'excluded', title: 'Not searched', ar: 'خارج البحث', note: 'consent withdrawn', icon: BanIcon },
]

const CHIP = {
  ACTIVE: ['Waiting', SearchIcon], BOARDED: ['Boarded', CheckIcon], CONSENT_WITHDRAWN: ['Consent withdrawn', BanIcon],
  NOT_IN_GALLERY: ['Not in gallery', BanIcon], PURGED: ['Template destroyed', LockIcon],
}

// After departure + purge there is no gallery any more: one full-width row, "Gallery destroyed".
const PURGED_AREA = { id: 'purged', title: 'Gallery destroyed', ar: 'تم إتلاف القوالب', note: 'crypto-shred after departure', icon: LockIcon }

function areaOf(entry, purged) {
  if (purged) return 'purged'
  if (entry.status === 'ACTIVE') return 'waiting'
  if (entry.status === 'BOARDED') return 'boarded'
  return 'excluded'
}

/** Score bar 0..1 with the threshold as a labelled line; the fill animates via a CSS width transition. */
function ScoreBar({ score, threshold }) {
  return (
    <div className="scorebar">
      <div className={`scorebar-fill ${score >= threshold ? 'above' : 'below'}`} style={{ width: `${Math.max(score, 0) * 100}%` }} />
      <div className="scorebar-t" style={{ left: `${threshold * 100}%` }} />
    </div>
  )
}

function Tile({ entry, flipRef, purged, scan }) {
  const { scores, current, revealed, top1, top2, threshold } = scan
  const status = purged && entry.status !== 'CONSENT_WITHDRAWN' ? 'PURGED' : entry.status
  const [label, Icon] = CHIP[status]
  const score = scores?.[entry.profile_id]
  const ring = revealed && entry.profile_id === top1 ? 'ring-top1' : revealed && entry.profile_id === top2 ? 'ring-top2' : ''
  return (
    <div ref={flipRef(entry.profile_id)} className={`tile ${ring} ${current === entry.profile_id ? 'scanning' : ''}`}>
      <div className="thumb">
        <img src={api.thumbnailUrl(entry.profile_id)} alt={`Reference ${entry.profile_id}`} />
        {entry.is_synthetic && <span className="synthetic-tag">synthetic</span>}
      </div>
      <div className="tile-body">
        <div className="tile-id">
          {entry.profile_id}
          {ring && <span className={`rank ${ring}`}>{ring === 'ring-top1' ? 'Top 1' : 'Top 2'}</span>}
        </div>
        <div className={`chip chip-${status}`}><Icon size={14} /> {label}</div>
        {revealed && score != null ? (
          <div className="tile-score">
            <span className="score-num"><CountUp value={score} /></span>
            <ScoreBar score={score} threshold={threshold} />
          </div>
        ) : <div className="tile-score placeholder">{entry.status === 'ACTIVE' ? 'not scored yet' : 'not searched'}</div>}
      </div>
    </div>
  )
}

/**
 * The flight's reference gallery in three areas. Areas come ONLY from the gallery fetched from the gate-service,
 * so a tile moves (animated by useFlip) when the database changes, never because of a front-end guess.
 */
export default function GalleryBoard({ gallery, scan, previousWaiting }) {
  const flipRef = useFlip(JSON.stringify(gallery?.entries ?? []))
  if (!gallery) return <section className="card gallery-card"><div className="capture-empty">Loading DEMO01 gallery…</div></section>
  const purged = gallery.flight_status === 'PURGED'
  const areas = purged ? [PURGED_AREA] : AREAS
  const waiting = gallery.entries.filter((e) => areaOf(e, purged) === 'waiting').length
  return (
    <section className="card gallery-card">
      <header className="card-head">
        <SearchIcon size={18} /> Flight gallery · {gallery.flight_id}
        <span className="waiting-count">
          Waiting to board <strong>{waiting}</strong>
          {previousWaiting != null && previousWaiting !== waiting && <span className="was"> (was {previousWaiting})</span>}
        </span>
        <span className="threshold-key"><span className="t-swatch" /> threshold T = {scan.threshold}</span>
      </header>
      {purged && <div className="purged-note"><LockIcon size={16} /> Gate closed: templates and the flight key were destroyed (crypto-shred).</div>}
      <div className="areas">
        {areas.map((area) => (
          <div key={area.id} className={`area area-${area.id}`}>
            <div className="area-head">
              <area.icon size={16} /> {area.title} <span lang="ar" dir="rtl">{area.ar}</span>
              <span className="area-note">{area.note}</span>
            </div>
            <div className="area-tiles">
              {gallery.entries.filter((e) => areaOf(e, purged) === area.id).map((e) => (
                <Tile key={e.profile_id} entry={e} flipRef={flipRef} purged={purged} scan={scan} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}
