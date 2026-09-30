import { useLayoutEffect, useRef } from 'react'

export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * "FLIP" animation for tiles that move between areas: after React re-renders, compare each tile's new position with
 * the old one and animate from old to new (Web Animations API, no library). Positions only change when the gallery
 * fetched from the server changes, so every movement reflects the database.
 * Usage: const flipRef = useFlip(dep); <div ref={flipRef(id)}>
 */
export function useFlip(dependency) {
  const nodes = useRef(new Map())
  const rects = useRef(new Map())

  useLayoutEffect(() => {
    nodes.current.forEach((el, id) => {
      const before = rects.current.get(id)
      const after = el.getBoundingClientRect()
      if (before && !reducedMotion() && (before.left !== after.left || before.top !== after.top)) {
        el.animate(
          [{ transform: `translate(${before.left - after.left}px, ${before.top - after.top}px)` }, { transform: 'none' }],
          { duration: 480, easing: 'cubic-bezier(.2,.8,.2,1)' },
        )
      }
      rects.current.set(id, after)
    })
  }, [dependency])

  return (id) => (el) => {
    if (el) nodes.current.set(id, el)
    else nodes.current.delete(id)
  }
}
