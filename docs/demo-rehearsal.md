# X-ray demo: rehearsal checklist (flight DEMO01)

Demo view only: it shows system internals. A production gate would never display this.

## 1. Start everything (four terminals, repo root `D:\POC`)
1. `docker compose -f ops/docker-compose.yml up -d --wait db`
2. `cd model-service; $env:DEVICE="cuda"; ..\.venv\Scripts\python -m uvicorn app.main:app --port 8001`
3. `cd mocks; $env:DEMO_XRAY="true"; ..\.venv\Scripts\python -m uvicorn main:app --port 8002`
4. `cd gate-service; $env:SPRING_PROFILES_ACTIVE="demo"; .\mvnw.cmd spring-boot:run`
5. First time after starting the mocks: `.\.venv\Scripts\python tools\demo_scenario.py --reset` (seeds everything, ~20 s).
6. `cd gate-console; npm run dev`, open http://localhost:5173 in Chrome, press **F11** (full screen = 1920×1080).

Check: the header says **BOARDING**, "Waiting to board **5**", both breaker lights green, and the Presenter shows
"Probe 1 of 15".

## 2. Reset between rehearsals
Click **Reset demo** (bottom right) and wait ~10 s for "DEMO01 reset: 5 passengers waiting". It deletes only DEMO01's
rows, clears every fault, closes the breakers and re-seeds the 5 passengers. Captures sent during a reset get
"Biometric boarding is closed" (correct: the flight is being rebuilt).

## 3. The script (click the gold button once per step)

| Step | You click | You should see |
|---|---|---|
| Probe 1 | Send capture (P001 night) | Amber **Please see an agent**, NO_MATCH; P001 0.420 below the T line |
| Probe 2 | Send capture (P001 daylight) | Amber, **AMBIGUOUS**: P001 0.604 and P001-TWIN 0.602 ringed Top 1 / Top 2, margin 0.002 |
| Action | Do it: withdraw P001-TWIN's consent | P001-TWIN's tile glides to **Not searched**; waiting 5 → 4 |
| Probe 3 | Send capture (P001 daylight) | Green **Please board**, P001 tile glides to **Boarded**; waiting 4 → 3 |
| Probe 4 | Send capture (P001 daylight) | Amber NO_MATCH: P001 is no longer scored (not searched) |
| Probe 5 | Send capture (replay) | Green again with **replayed #…**: same event, nothing re-runs, no tile moves |
| Probe 6 | Send capture (stranger) | Amber NO_MATCH, best score ~0.07 |
| Probe 7 | Send capture (empty frame) | Amber **NO_FACE**, no brackets |
| Probe 8 | Send capture (two people) | Amber **MULTI_FACE**, two bracket boxes |
| Probe 9 | Send capture (too far) | Neutral **Please step closer**, LOW_QUALITY |
| Action | Do it: S01 invalid at the identity provider | Nothing moves (the IdP holds this state) |
| Probe 10 | Send capture (S01) | Amber **IDP_REJECTED**: S01 matched 0.807 but was not confirmed |
| Action | Do it: identity provider slow (2 s) | — |
| Probe 11 | Send capture (S02) | Amber **IDP_UNAVAILABLE** after ~300 ms; identity light turns **red (Open)** |
| Probe 12 | Send capture (S03) | Amber, "Circuit breaker OPEN: manual in 0.x ms, without calling the identity provider" |
| Action | Do it: clear fault + cool down | Button counts down 6 → 1 |
| Probe 13 | Send capture (S03) | Green BOARD; light goes amber (trial call) then **green**; S03 → Boarded |
| Action | Do it: face service down | — |
| Probe 14 | Send capture (S02) | Amber **MODEL_UNAVAILABLE** within ~40 ms |
| Action | Do it: close the gate and depart | Header flips to **GATE CLOSED** (~5 s); tiles move to **Gallery destroyed** |
| Probe 15 | Send capture (S02) | Amber **GALLERY_UNAVAILABLE**: "templates were destroyed" |

Every probe adds a line to **Expected vs actual**; a red line means the outcome differed (see below). **Free mode**
(top right tab) lets you send any capture or do any action at any time; it does not move the script pointer.

## 4. Live webcam for the P001 scenes (probes 1-4)
Choose **Live webcam** on the step card, then **Send capture**: a 3-2-1 ring counts down and one frame is taken.
The comparison card shows **Live score vs P001** and how far it is from T = 0.44.

If a live capture falls below the threshold on a step that expects BOARD (or AMBIGUOUS):
- the line turns red and the step stays where it is: nothing in the database changed, so you can simply **retry**;
- face the camera, move closer (face at least ~200 px wide in the preview), light from the front, no glasses glare;
- or switch the step to **Stored photo** and send again. The stored photo always gives the scripted outcome.
For probe 1 (poor capture) a live capture needs to be genuinely poor (dim light, look away); otherwise use the stored photo.

## 5. Before recording
- Do one full "Play all (verification)" after a reset: all 15 lines must be green. Then **Reset demo** again.
- Close other tabs, hide the bookmarks bar, full screen (F11).
- Keep the attribution line in the footer visible; if you share the recording, keep this notice with it:
  "Synthetic faces: DigiFace-1M, Microsoft, research licence (R-UDA v1.0, non-commercial)".
