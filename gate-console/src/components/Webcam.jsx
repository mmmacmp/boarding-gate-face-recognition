import { useEffect, useRef, useState } from 'react'

/** Live preview from the browser camera and a capture button. The photo scenes work without it. */
export default function Webcam({ onCapture, disabled }) {
  const videoRef = useRef(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let stream = null
    navigator.mediaDevices
      .getUserMedia({ video: { width: 1280, height: 720 } })
      .then((s) => {
        stream = s
        videoRef.current.srcObject = s
      })
      .catch((e) => setError(e.message))
    return () => stream?.getTracks().forEach((track) => track.stop()) // switch the camera off when hidden
  }, [])

  function capture() {
    const video = videoRef.current
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0)
    canvas.toBlob((blob) => blob && onCapture(blob), 'image/jpeg', 0.9) // a canvas JPEG carries no EXIF
  }

  if (error) return <p className="notice">Camera not available ({error}). Use the demo photos above.</p>
  return (
    <div className="webcam">
      <video ref={videoRef} autoPlay playsInline muted />
      <button className="capture-button" disabled={disabled} onClick={capture}>
        Capture
      </button>
    </div>
  )
}
