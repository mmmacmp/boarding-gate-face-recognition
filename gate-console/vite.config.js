import { execFile } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const ROOT = path.resolve(import.meta.dirname, '..')
const DATA = path.join(ROOT, 'data')
const PYTHON = path.join(ROOT, '.venv', 'Scripts', 'python.exe')

/**
 * The only captures the console may fetch: an allowlist of the DEMO01 script's probes, never an arbitrary path.
 * Synthetic ones (DigiFace-1M, research licence) are shown with an attribution line in the page footer.
 */
const PROBES = {
  'p001-easy': ['volunteers/P001', 'probe'],
  'p001-hard': ['volunteers/P001', 'probe_02'],
  s01: ['demo/S01', 'probe'],
  s02: ['demo/S02', 'probe'],
  s03: ['demo/S03', 'probe'],
  stranger: ['synthetic/STRANGER', 'probe'],
  'no-face': ['demo/special', 'no_face'],
  'two-faces': ['demo/special', 'two_faces'],
  'tiny-face': ['demo/special', 'tiny_face'],
}

function probeFile(name) {
  const [dir, stem] = PROBES[name] ?? []
  if (!dir) return null
  const folder = path.join(DATA, dir)
  const file = fs.existsSync(folder) && fs.readdirSync(folder).find((f) => path.parse(f).name === stem)
  return file ? path.join(folder, file) : null
}

/** Dev-server-only helpers: serve demo captures, and run the same reset as the demo script. */
function demoTools() {
  return {
    name: 'demo-tools',
    configureServer(server) {
      server.middlewares.use('/demo/probe/', (req, res) => {
        const file = probeFile(decodeURIComponent(req.url.slice(1)))
        if (!file) {
          res.statusCode = 404
          return res.end('unknown demo capture')
        }
        res.setHeader('Content-Type', 'image/jpeg')
        fs.createReadStream(file).pipe(res)
      })
      // Fast reset of the 5-passenger DEMO01 flight only (POC001/POC002 untouched).
      server.middlewares.use('/demo/reset-flight', (req, res) => {
        if (req.method !== 'POST') {
          res.statusCode = 405
          return res.end()
        }
        execFile(PYTHON, ['tools/demo_scenario.py', '--reset-demo-flight'], { cwd: ROOT }, (error, stdout, stderr) => {
          res.setHeader('Content-Type', 'application/json')
          res.statusCode = error ? 500 : 200
          res.end(JSON.stringify({ ok: !error, output: (error ? stderr : stdout).trim() }))
        })
      })
    },
  }
}

// The browser only talks to this dev server; the proxy forwards to each service, so no CORS is needed anywhere.
const service = (port, prefix) => ({
  target: `http://localhost:${port}`,
  rewrite: (p) => p.replace(new RegExp(`^/${prefix}`), ''),
})

export default defineConfig({
  plugins: [react(), demoTools()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/gate': service(8081, 'gate'),
      '/mocks': service(8002, 'mocks'),
      '/model': service(8001, 'model'),
    },
  },
})
