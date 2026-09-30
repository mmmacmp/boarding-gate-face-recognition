import { reasonText, shownProfile } from '../reasons.js'

/** Airport-signage style banner. `key` changes with every event, so React re-mounts it and the CSS entry
 *  animation plays again for each new decision. */
export default function DecisionBanner({ event, busy }) {
  if (busy) return <div className="banner waiting">Checking…</div>
  if (!event) return <div className="banner idle">Waiting for passenger</div>

  const board = event.decision === 'BOARD'
  return (
    <div key={event.event_id ?? event.reason} className={`banner ${board ? 'board' : 'manual'}`}>
      <div className="banner-title">{board ? 'BOARD' : 'PLEASE SEE AN AGENT'}</div>
      <div className="banner-text">{board ? `Welcome, ${shownProfile(event)}` : reasonText(event.reason)}</div>
      <div className="banner-code">
        {event.reason} · profile {shownProfile(event)}
      </div>
    </div>
  )
}
