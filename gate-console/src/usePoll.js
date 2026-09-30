import { useEffect, useState } from 'react'

/**
 * Calls load() now and then every intervalMs; returns { data, error }.
 * The cleanup function stops the timer when the component goes away (like cancelling an asyncio task).
 */
export function usePoll(load, intervalMs = 1000) {
  const [state, setState] = useState({ data: null, error: null })
  useEffect(() => {
    let alive = true
    const tick = () =>
      load()
        .then((data) => alive && setState({ data, error: null }))
        .catch((error) => alive && setState((s) => ({ data: s.data, error: error.message })))
    tick()
    const timer = setInterval(tick, intervalMs)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [load, intervalMs])
  return state
}
