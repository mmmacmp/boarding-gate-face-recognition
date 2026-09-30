// Plain-English text for every reason code. Shown on the gate banner instead of raw codes or errors.
export const REASONS = {
  MATCHED: 'Identity confirmed. Please proceed.',
  NO_FACE: 'No face detected. Please look at the camera.',
  LOW_QUALITY: 'Face too small in the picture. Please step closer.',
  MULTI_FACE: 'More than one person in the gate. One passenger at a time, please.',
  NO_MATCH: 'Not recognised for this flight. An agent will check your documents.',
  AMBIGUOUS: 'Similar-looking passengers on this flight. An agent will check your documents.',
  GALLERY_UNAVAILABLE: 'Biometric boarding is not open for this flight.',
  MODEL_UNAVAILABLE: 'Face service unavailable. Manual boarding.',
  MODEL_MISMATCH: 'Face service version mismatch. Manual boarding.',
  IDP_UNAVAILABLE: 'Identity service unavailable. Manual boarding.',
  IDP_REJECTED: 'Identity check not confirmed. An agent will check your documents.',
  ALREADY_BOARDED: 'Already boarded at another gate. Please see an agent.',
  INTERNAL_ERROR: 'Gate error. Manual boarding.',
  DB_UNAVAILABLE: 'Gate records unavailable. Manual boarding.',
  GATE_UNREACHABLE: 'Gate service unreachable. Manual boarding.',
}

export const reasonText = (code) => REASONS[code] ?? 'Manual boarding.'

/** The one privacy rule of the UI: an identity is shown only for BOARD, everything else shows a dash. */
export const shownProfile = (event) => (event.decision === 'BOARD' && event.profile_id ? event.profile_id : '—')
