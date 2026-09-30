import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import './styles.css'
import './theme-light.css'

// Light theme by default; the original dark theme is still available with http://localhost:5173/?theme=dark
document.documentElement.dataset.theme = new URLSearchParams(location.search).get('theme') ?? 'light'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
