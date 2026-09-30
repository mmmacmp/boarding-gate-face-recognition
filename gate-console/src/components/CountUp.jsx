import { useEffect, useState } from 'react'
import { reducedMotion } from '../useFlip.js'

/** A number that counts up to `value` over ~450 ms (ease-out), e.g. a score appearing after the scan. */
export default function CountUp({ value, digits = 3 }) {
  const [shown, setShown] = useState(reducedMotion() ? value : 0)
  useEffect(() => {
    if (reducedMotion()) return setShown(value)
    let frame
    const start = performance.now()
    const tick = (now) => {
      const t = Math.min((now - start) / 450, 1)
      setShown(value * (1 - (1 - t) ** 3))
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value])
  return <>{shown.toFixed(digits)}</>
}
