import { shownProfile } from '../reasons.js'

/** Newest 20 decisions. The profile column shows an id only for BOARD, a dash for everything else. */
export default function RecentEvents({ events }) {
  if (!events.length) return <p className="muted">No events yet.</p>
  return (
    <table className="events">
      <thead>
        <tr>
          <th>Time</th>
          <th>Gate</th>
          <th>Flight</th>
          <th>Decision</th>
          <th>Reason</th>
          <th>Profile</th>
          <th>Top-1</th>
        </tr>
      </thead>
      <tbody>
        {events.map((e) => (
          <tr key={e.event_id}>
            <td>{new Date(e.created_at).toLocaleTimeString()}</td>
            <td>{e.gate_id}</td>
            <td>{e.flight_id}</td>
            <td className={e.decision === 'BOARD' ? 'txt-board' : 'txt-manual'}>{e.decision}</td>
            <td>{e.reason}</td>
            <td>{shownProfile(e)}</td>
            <td>{e.top1 == null ? '—' : e.top1.toFixed(3)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
