<handoff phase="1">
context_summary: The airline runs consent-based facial recognition at DXB T3 check-in, the Concourse B lounge (5 entry points) and some boarding gates on Concourses A/B/C: AED 85m and 200+ cameras (3 Nov 2025), 18+ loyalty-programme members only, identity anchored to identity-authority profiles (temporary profiles for first-time visitors). The identity authority separately runs Smart Gates (>9.3m users Jan–Aug 2026) and the Red Carpet walk-through corridor (3.4 s average, 745,188 users Jan–Aug 2026; built by emaratech with Paravision). DXB handled 95.2m passengers in 2025 (forecast 99.5m in 2026). The gate problems most relevant to airline IT are integration, 1:N threshold and gallery logic, fallback, operations, and consent/security. No airline-side vendor, gate count, match rate or architecture is public.
top_problems:
  - id: P13
    title: Multi-system integration at the gate
    one_line: Keep app, loyalty, DCS boarded-state, identity-authority profile and gate events consistent in real time.
    category: integration
  - id: P1
    title: 1:N threshold and flight-gallery logic
    one_line: Balance FNIR vs in-gallery false positives with manifest galleries, boarded-state exclusion and score-margin rules.
    category: accuracy
  - id: P14
    title: Resilience and safe fallback
    one_line: Degrade to manual passport/boarding-pass checks without blocking boarding when cameras, network or the identity authority fail.
    category: resilience
  - id: P15
    title: Production operations and model lifecycle
    one_line: Per-gate latency/match/fallback telemetry, SLAs, on-call, and safe matcher upgrades with template re-extraction.
    category: operations
  - id: P10
    title: Consent enforcement for walk-through capture
    one_line: Cameras see non-enrolled people; enforce consent before acting on or logging matches, and minimise retained data.
    category: privacy
key_facts:
  - fact: The airline invested AED 85m in 200+ biometric cameras across T3; recognition from 1 m; some gates on Concourses A, B, C; 18+ loyalty-programme registration; matching against identity-authority profiles
    source_url: https://www.emirates.com/media-centre/speedy-seamless-secure--emirates-invests-in-innovative-facial-recognition-technology-across-dubai-international-dxb/
    date: 2025-11-03
  - fact: The airline warns non-users that biometric zone systems "may automatically recognize you"; passport/boarding pass remains available at every touchpoint
    source_url: https://www.emirates.com/us/english/experience/contactless-journey/biometrics/
    date: 2025-10 (last updated)
  - fact: Red Carpet averaged 3.4 s vs 12.5 s; 745,188 users and >9.3m Smart Gate users in Jan–Aug 2026; Click and Travel pilot 31,373 transactions
    source_url: https://www.arabianbusiness.com/business/transport/dubai-airports-immigration-control
    date: 2026 (Jan–Aug data)
  - fact: Red Carpet developed and implemented by emaratech using Paravision biometric technology
    source_url: https://www.paravision.ai/news/paravision-collaborates-with-emaratech-to-power-red-carpet-smart-corridor-at-dubai-international-airport/
    date: 2026-01-06
  - fact: DXB handled 95.2m passengers in 2025; 99.5m forecast for 2026
    source_url: https://www.thenationalnews.com/business/aviation/2026/02/11/dubai-airport-handles-record-952-million-passengers-in-2025-amid-growth-of-emirates-airline/
    date: 2026-02-11
  - fact: NIST paperless-travel simulations use 420-person boarding galleries; in-gallery false positives can cause false negatives for the correct passenger
    source_url: https://nvlpubs.nist.gov/nistpubs/ir/2021/NIST.IR.8381.pdf
    date: 2021-07 (OLD)
  - fact: EDPB finds centralised template storage without passenger-held key incompatible with GDPR Art. 25 for airport flow facilitation
    source_url: https://www.edpb.europa.eu/system/files/documents/2024-05/edpb_opinion_202411_facialrecognitionairports_en.pdf
    date: 2024-05-23 (OLD, operative)
  - fact: ISO/IEC 29794-5 face image quality standard published April 2025 with OFIQ as reference implementation
    source_url: https://www.eulisa.europa.eu/news-and-events/news/eu-lisa-joins-maintenance-effort-biometric-open-source-face-image-quality-tool
    date: 2025
unknowns: [Airline boarding-gate vendor and matcher, number of biometric gates, enrollment and adoption numbers, gate-level FNIR/FPIR and latency, edge vs central matching architecture, gallery definition, retention periods, whether app enrollment reads the passport chip, PAD approach, CBP biometric boarding current status, status of biometric Connections, PDPL Executive Regulations status, niqab handling at gates]
</handoff>
