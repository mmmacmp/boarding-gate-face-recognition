// The presenter script for flight DEMO01: 15 probes (gate captures) and 6 actions between them.
// Nothing runs by itself: the console shows the next step and waits for a click.
//   probe:  which capture to send (allowlisted in vite.config.js); webcam: P001 may use a live capture instead
//   expect: the reason code the step should produce; the console shows expected vs actual after every probe

export const FLIGHT = 'DEMO01'

export const PROBES = {
  'p001-easy': { label: 'P001 · daylight', profile: 'P001' },
  'p001-hard': { label: 'P001 · night, low angle', profile: 'P001' },
  s01: { label: 'S01', profile: 'S01' },
  s02: { label: 'S02', profile: 'S02' },
  s03: { label: 'S03', profile: 'S03' },
  stranger: { label: 'Stranger (not on the flight)', profile: null },
  'no-face': { label: 'Empty frame (no face)', profile: null },
  'two-faces': { label: 'Two people in the gate', profile: null },
  'tiny-face': { label: 'Passenger too far away', profile: null },
}

export const STEPS = [
  { probe: 'p001-hard', webcam: true, case: 4, title: 'Poor capture', expect: 'NO_MATCH',
    say: 'P001 arrives at night. The capture is too poor to reach the threshold, so the gate does not guess.' },
  { probe: 'p001-easy', webcam: true, case: 5, title: 'Look-alike on the same flight', expect: 'AMBIGUOUS',
    say: 'A daylight retake. P001-TWIN (simulated look-alike) scores almost the same: too close to call.' },
  { action: 'withdrawTwin', title: 'Withdraw P001-TWIN’s consent',
    say: 'The look-alike opts out. Watch the tile move to “Not searched”.' },
  { probe: 'p001-easy', webcam: true, case: 6, cases: [1], title: 'Consent withdrawn, then a clean match', expect: 'MATCHED',
    say: 'Only consented passengers are searched now: P001 boards and moves to “Boarded”.' },
  { probe: 'p001-easy', webcam: true, case: 2, title: 'Same passenger again', expect: 'NO_MATCH',
    say: 'Boarded passengers are no longer searched, so a second attempt cannot board twice.' },
  { probe: 'replay', case: 3, title: 'Duplicate request (same Idempotency-Key)', expect: 'MATCHED', replay: true,
    say: 'The gate retries the boarding request after a network glitch: the stored decision comes back, nothing runs again.' },
  { probe: 'stranger', case: 7, title: 'Stranger not on this flight', expect: 'NO_MATCH',
    say: 'Someone who is not on the manifest: nobody scores near the threshold, and no identity is recorded.' },
  { probe: 'no-face', case: 8, title: 'No face in the image', expect: 'NO_FACE',
    say: 'The camera fired on an empty frame: nothing to compare.' },
  { probe: 'two-faces', case: 9, title: 'Two people in the gate', expect: 'MULTI_FACE',
    say: 'Two faces in the zone: the gate refuses to pick one, and no template is computed for either.' },
  { probe: 'tiny-face', case: 10, title: 'Face too small', expect: 'LOW_QUALITY',
    say: 'The passenger stands too far away: a retake prompt, not a rejection.' },
  { action: 'idpInvalidS01', title: 'Identity provider: S01 is not valid for travel',
    say: 'We tell the mock identity provider to answer INVALID for S01.' },
  { probe: 's01', case: 11, title: 'Identity provider says INVALID', expect: 'IDP_REJECTED',
    say: 'The face matches S01 clearly, but the identity provider does not confirm: a human checks.' },
  { action: 'idpSlow', title: 'Identity provider becomes slow (2 s)', say: 'Every call now takes 2 seconds.' },
  { probe: 's02', case: 12, title: 'Identity provider too slow', expect: 'IDP_UNAVAILABLE',
    say: 'The gate waits at most 300 ms, then goes to manual. This failure opens the circuit breaker.' },
  { probe: 's03', case: 13, title: 'Circuit breaker OPEN', expect: 'IDP_UNAVAILABLE',
    say: 'The breaker is open: the gate answers instantly, without calling the identity provider at all.' },
  { action: 'recover', title: 'Clear the fault and let the breaker cool down',
    say: 'After a short cool-down the breaker lets one trial call through.' },
  { probe: 's03', case: 13, title: 'Recovery', expect: 'MATCHED',
    say: 'The trial call succeeds: the breaker closes and S03 boards.' },
  { action: 'modelDown', title: 'Face service goes down', say: 'The model-service now refuses every request.' },
  { probe: 's02', case: 14, title: 'Face service down', expect: 'MODEL_UNAVAILABLE',
    say: 'No embedding, no decision: manual boarding within milliseconds.' },
  { action: 'depart', title: 'Close the gate and depart',
    say: 'Clear the fault, close the flight. After a short grace period the templates and the flight key are destroyed.' },
  { probe: 's02', case: 15, title: 'After departure', expect: 'GALLERY_UNAVAILABLE',
    say: 'The gallery is gone (crypto-shred): nothing left to match against.' },
]

export const needsWebcamChoice = (step) => Boolean(step?.webcam)
