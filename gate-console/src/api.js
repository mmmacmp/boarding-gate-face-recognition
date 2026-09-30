// All HTTP calls of the console. Paths go through the Vite dev-server proxy (see vite.config.js).

async function json(response) {
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

const post = (url, body) =>
  fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(json)

/** Send one capture to a gate. Any failure becomes MANUAL, exactly like a real gate would behave. */
export async function sendCapture(imageBlob, flightId, gateId = 'G1') {
  const form = new FormData()
  form.append('image', imageBlob, 'capture.jpg')
  form.append('flight_id', flightId)
  try {
    const response = await fetch(`/gate/v1/gates/${gateId}/events`, {
      method: 'POST',
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: form,
    })
    const body = await response.json().catch(() => ({}))
    if (response.ok) return body
    return { decision: 'MANUAL', reason: body.reason ?? 'GATE_UNREACHABLE' } // e.g. 503 DB_UNAVAILABLE
  } catch {
    return { decision: 'MANUAL', reason: 'GATE_UNREACHABLE' }
  }
}

export const api = {
  probe: (name) => fetch(`/demo/probe/${name}`).then((r) => (r.ok ? r.blob() : Promise.reject(new Error(r.status)))),
  policy: () => fetch('/gate/v1/policy').then(json),
  modelHealth: () => fetch('/model/health').then(json),
  flight: (id) => fetch(`/gate/v1/flights/${id}`).then(json),
  recentEvents: () => fetch('/gate/v1/events').then(json),
  breakers: () => fetch('/gate/actuator/circuitbreakers').then(json),
  breakerEvents: () => fetch('/gate/actuator/circuitbreakerevents').then(json),
  idpFaults: () => fetch('/mocks/admin/faults').then(json),
  modelFaults: () => fetch('/model/admin/faults').then(json),
  setIdpFaults: (faults) => post('/mocks/admin/faults', faults),
  setModelFaults: (faults) => post('/model/admin/faults', faults),
  withdrawConsent: (profileId) =>
    fetch(`/gate/v1/consents/${encodeURIComponent(profileId)}`, { method: 'DELETE' }).then(json),
  resetDemo: () => fetch('/demo/reset', { method: 'POST' }).then(json),
}
