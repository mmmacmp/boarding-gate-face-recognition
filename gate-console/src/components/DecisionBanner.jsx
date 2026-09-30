import { REASONS, TITLES, kindOf } from '../text.js'
import { AlertIcon, CheckIcon, ReplayIcon, StepCloserIcon } from './Icons.jsx'

const ICON = { board: CheckIcon, manual: AlertIcon, retake: StepCloserIcon }

/**
 * Bilingual decision signage. BOARD = green + check, MANUAL = amber + warning, LOW_QUALITY = neutral retake prompt
 * with a "step closer" icon: never colour alone. The `key` re-mounts it per event so the reveal plays again.
 */
export default function DecisionBanner({ event, busy, replayOf }) {
  if (busy && !event) return <div className="banner banner-wait"><span className="pulse-dot" /> Checking…</div>
  if (!event) return <div className="banner banner-wait">Waiting for passenger · <span lang="ar" dir="rtl">بانتظار المسافر</span></div>

  const kind = kindOf(event)
  const Icon = ICON[kind]
  const reason = REASONS[event.reason] ?? REASONS.INTERNAL_ERROR
  return (
    <div key={`${event.event_id}-${event.key}`} className={`banner banner-${kind}`} role="status">
      <Icon size={52} className="banner-icon" />
      <div className="banner-body">
        <div className="banner-title">
          <span>{TITLES[kind].en}</span>
          <span lang="ar" dir="rtl">{TITLES[kind].ar}</span>
        </div>
        <div className="banner-reason">
          <span>{reason.en}</span>
          <span lang="ar" dir="rtl">{reason.ar}</span>
        </div>
        <div className="banner-code">
          {event.reason}{event.decision === 'BOARD' ? ` · ${event.profile_id}` : ' · profile —'}
          {replayOf && <span className="replay-tag"><ReplayIcon size={14} /> replayed #{replayOf}</span>}
        </div>
      </div>
    </div>
  )
}
