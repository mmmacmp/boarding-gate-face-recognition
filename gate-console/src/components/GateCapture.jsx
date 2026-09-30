import { useEffect, useRef, useState } from 'react'
import { CameraIcon } from './Icons.jsx'

/** Animated corner brackets around one detected face (image pixel coordinates; the SVG shares the image's size). */
function FaceBrackets({ box }) {
  const [x1, y1, x2, y2] = box
  const l = Math.min(x2 - x1, y2 - y1) * 0.25
  const d = [
    `M${x1} ${y1 + l}V${y1}H${x1 + l}`, `M${x2 - l} ${y1}H${x2}V${y1 + l}`,
    `M${x2} ${y2 - l}V${y2}H${x2 - l}`, `M${x1 + l} ${y2}H${x1}V${y2 - l}`,
  ]
  return d.map((path, i) => <path key={i} className="bracket" d={path} pathLength="1" />)
}

/**
 * Live camera preview with a 3-second countdown ring. The parent gets a capture() function through cameraRef:
 * capture() counts down, grabs one frame, and resolves with a JPEG Blob (a canvas JPEG carries no EXIF).
 */
function LiveCamera({ cameraRef }) {
  const videoRef = useRef(null)
  const [count, setCount] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let stream = null
    navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 } })
      .then((s) => { stream = s; videoRef.current.srcObject = s })
      .catch((e) => setError(e.message))
    cameraRef.current = {
      capture: async () => {
        for (let n = 3; n > 0; n--) {
          setCount(n)
          await new Promise((r) => setTimeout(r, 1000))
        }
        setCount(null)
        const v = videoRef.current
        const canvas = document.createElement('canvas')
        canvas.width = v.videoWidth
        canvas.height = v.videoHeight
        canvas.getContext('2d').drawImage(v, 0, 0)
        return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
      },
    }
    return () => {
      cameraRef.current = null
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [cameraRef])

  if (error) return <div className="capture-empty">Camera not available ({error}). Use the stored photo.</div>
  return (
    <div className="live">
      <video ref={videoRef} autoPlay playsInline muted />
      <span className="live-badge"><CameraIcon size={16} /> Live</span>
      {count && (
        <div className="countdown" key={count}>
          <svg viewBox="0 0 100 100"><circle cx="50" cy="50" r="44" className="ring-bg" /><circle cx="50" cy="50" r="44" className="ring" /></svg>
          <span>{count}</span>
        </div>
      )}
    </div>
  )
}

/**
 * "Gate capture": the probe image, a scanning line while the gate processes it, then brackets around every face
 * the model-service found (from the X-ray response). In webcam mode it shows the live preview instead.
 */
export default function GateCapture({ capture, scanning, faceBoxes, webcam, cameraRef }) {
  const [size, setSize] = useState(null)
  return (
    <section className="card capture-card">
      <header className="card-head"><CameraIcon size={18} /> Gate capture <span className="muted" lang="ar" dir="rtl">صورة البوابة</span></header>
      <div className="capture-stage">
        {webcam ? <LiveCamera cameraRef={cameraRef} />
          : capture ? (
            <div className="capture-frame" style={size ? { aspectRatio: `${size.w} / ${size.h}` } : undefined}>
              <img src={capture.url} alt={capture.label}
                   onLoad={(e) => setSize({ w: e.target.naturalWidth, h: e.target.naturalHeight })} />
              {scanning && <div className="scanline" />}
              {size && !scanning && (
                <svg className="faces" viewBox={`0 0 ${size.w} ${size.h}`} preserveAspectRatio="none">
                  {faceBoxes.map((b, i) => <FaceBrackets key={`${capture.url}-${i}`} box={b} />)}
                </svg>
              )}
            </div>
          ) : <div className="capture-empty">Waiting for a passenger</div>}
      </div>
      <footer className="capture-caption">{webcam ? 'Live webcam · 3 s countdown on capture' : capture?.label ?? ' '}</footer>
    </section>
  )
}
