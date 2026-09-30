// All HTTP calls of the console. Paths go through the Vite dev-server proxy (see vite.config.js).

async function json(response) {
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.status === 204 ? null : response.json()
}

const send = (method, url, body) =>
  fetch(url, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }).then(json)

/** Send one capture to gate G1. Any failure becomes MANUAL, exactly like a real gate would behave. */
export async function sendCapture(imageBlob, flightId, key = crypto.randomUUID()) {
  const form = new FormData()
  form.append('image', imageBlob, 'capture.jpg')
  form.append('flight_id', flightId)
  try {
    const response = await fetch('/gate/v1/gates/G1/events', {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
      body: form,
    })
    const body = await response.json().catch(() => ({}))
    if (response.ok) return { ...body, key }
    return { decision: 'MANUAL', reason: body.reason ?? 'GATE_UNREACHABLE', key }
  } catch {
    return { decision: 'MANUAL', reason: 'GATE_UNREACHABLE', key }
  }
}

export const api = {
  probe: (name) => fetch(`/demo/probe/${name}`).then((r) => (r.ok ? r.blob() : Promise.reject(new Error(r.status)))),
  thumbnailUrl: (profileId) => `/mocks/v1/demo/profiles/${encodeURIComponent(profileId)}/thumbnail`,
  policy: () => fetch('/gate/v1/policy').then(json),
  modelHealth: () => fetch('/model/health').then(json),
  flight: (id) => fetch(`/gate/v1/flights/${id}`).then(json),
  demoGallery: (id) => fetch(`/gate/v1/demo/flights/${id}/gallery`).then(json),
  breakers: () => fetch('/gate/actuator/circuitbreakers').then(json),
  breakerEvents: () => fetch('/gate/actuator/circuitbreakerevents').then(json),
  withdrawConsent: (id) => send('DELETE', `/gate/v1/consents/${encodeURIComponent(id)}`),
  grantConsent: (id) => send('POST', '/gate/v1/consents', { profile_id: id }),
  depart: (id) => send('POST', `/gate/v1/flights/${id}/depart`),
  setIdpStatus: (id, status) => send('PUT', `/mocks/admin/profiles/${encodeURIComponent(id)}/status`, { status }),
  setIdpFaults: (faults) => send('POST', '/mocks/admin/faults', faults),
  setModelFaults: (faults) => send('POST', '/model/admin/faults', faults),
  resetDemoFlight: () => send('POST', '/demo/reset-flight'),
}
