// Passenger-facing text in English and Arabic, and the one-sentence explanations for the presenter.
// Arabic wording: my translation; have a native speaker check it before a public recording.

export const TITLES = {
  board: { en: 'Please board', ar: 'تفضل بالصعود' },
  manual: { en: 'Please see an agent', ar: 'يرجى التوجه إلى الموظف' },
  retake: { en: 'Please step closer', ar: 'يرجى الاقتراب من الكاميرا' },
}

export const REASONS = {
  MATCHED: { en: 'Identity confirmed. Have a good flight.', ar: 'تم التحقق من الهوية. رحلة سعيدة.' },
  NO_FACE: { en: 'No face detected. Please look at the camera.', ar: 'لم يتم رصد وجه. يرجى النظر إلى الكاميرا.' },
  LOW_QUALITY: { en: 'Your face is too small in the picture.', ar: 'وجهك صغير جدًا في الصورة.' },
  MULTI_FACE: { en: 'One passenger at a time, please.', ar: 'مسافر واحد في كل مرة من فضلك.' },
  NO_MATCH: { en: 'We could not confirm you for this flight.', ar: 'لم نتمكن من تأكيدك لهذه الرحلة.' },
  AMBIGUOUS: { en: 'An agent will check your documents.', ar: 'سيتحقق الموظف من وثائقك.' },
  GALLERY_UNAVAILABLE: { en: 'Biometric boarding is closed for this flight.', ar: 'الصعود البيومتري مغلق لهذه الرحلة.' },
  MODEL_UNAVAILABLE: { en: 'Face check unavailable. Manual boarding.', ar: 'التحقق من الوجه غير متاح. صعود يدوي.' },
  MODEL_MISMATCH: { en: 'Face check unavailable. Manual boarding.', ar: 'التحقق من الوجه غير متاح. صعود يدوي.' },
  IDP_UNAVAILABLE: { en: 'Identity check unavailable. Manual boarding.', ar: 'التحقق من الهوية غير متاح. صعود يدوي.' },
  IDP_REJECTED: { en: 'An agent will check your documents.', ar: 'سيتحقق الموظف من وثائقك.' },
  ALREADY_BOARDED: { en: 'Already boarded. Please see an agent.', ar: 'تم تسجيل صعودك مسبقًا. يرجى مراجعة الموظف.' },
  INTERNAL_ERROR: { en: 'Gate error. Manual boarding.', ar: 'خطأ في البوابة. صعود يدوي.' },
  DB_UNAVAILABLE: { en: 'Gate records unavailable. Manual boarding.', ar: 'سجلات البوابة غير متاحة. صعود يدوي.' },
  GATE_UNREACHABLE: { en: 'Gate offline. Manual boarding.', ar: 'البوابة غير متصلة. صعود يدوي.' },
}

export const kindOf = (event) =>
  event.decision === 'BOARD' ? 'board' : event.reason === 'LOW_QUALITY' ? 'retake' : 'manual'

const f = (x) => x.toFixed(3)

/** One plain sentence for the presenter, built from what the server actually returned. */
export function explain(event, limits, { replayOf } = {}) {
  if (replayOf) return `Same Idempotency-Key: the stored decision #${replayOf} came back. No new search, no second boarding.`
  const ranked = Object.entries(event.xray?.scores ?? {})
  const [best, second] = ranked
  const T = limits?.threshold
  const D = limits?.margin
  const idMs = event.timings?.identity_ms
  switch (event.reason) {
    case 'MATCHED':
      return `${best[0]} scored ${f(best[1])}, above T ${T}${second ? `, ${f(best[1] - second[1])} clear of ${second[0]}` : ''}; the identity provider confirmed.`
    case 'NO_MATCH':
      return best ? `Best score ${f(best[1])} (${best[0]}) is below T ${T}: the gate does not guess.` : 'Nobody is left to search.'
    case 'AMBIGUOUS':
      return `${best[0]} ${f(best[1])} and ${second[0]} ${f(second[1])} are only ${f(best[1] - second[1])} apart (Δ ${D}): a human decides.`
    case 'IDP_REJECTED':
      return `${best[0]} matched (${f(best[1])}), but the identity provider answered INVALID.`
    case 'IDP_UNAVAILABLE':
      return idMs != null && idMs < 20
        ? `Circuit breaker OPEN: manual in ${idMs.toFixed(1)} ms, without calling the identity provider.`
        : `The identity provider did not answer within 300 ms, so the gate stopped waiting.`
    case 'MODEL_UNAVAILABLE':
      return 'The face service is down: no embedding, so manual boarding straight away.'
    case 'GALLERY_UNAVAILABLE':
      return 'The flight is closed and its templates were destroyed: nothing left to match.'
    case 'NO_FACE':
      return 'No face in the frame, so nothing is compared.'
    case 'MULTI_FACE':
      return 'Two faces in the zone: the gate will not pick one, and computes no template for either.'
    case 'LOW_QUALITY':
      return 'A face was seen but it is too small (under 80 px): ask for a retake, do not reject.'
    default:
      return 'Manual boarding.'
  }
}
