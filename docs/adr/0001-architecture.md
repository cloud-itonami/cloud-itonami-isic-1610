# ADR-0001: SawmillAdvisor ⊣ Sawmill Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-1610` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-1610` publishes an OSS blueprint for sawmill
**plant operations coordination** (production-batch lumber-grade/
volume/moisture-content data logging, saw-blade/planer/kiln
maintenance scheduling, safety-concern flagging, and outbound lumber
shipment coordination). Like every actor in this fleet, the blueprint
alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout
pattern established across the cloud-itonami fleet.

The closest domain analog is `cloud-itonami-isic-0220` (Logging):
both are back-office coordination actors for heavy-equipment
operations with a real physical safety dimension. Sawmilling differs
in one structural respect that shapes this design: 0220 coordinates
**field** operations (felling/skidding/hauling out in a forest
site/permit context); 1610 coordinates **plant** operations (a fixed
processing facility with saws, planers and kilns). The central ground-
truth entity is therefore a **production batch** moving through a
**plant** (not a **site** being harvested under a **permit**), and the
domain's HARD invariant about pre-verification applies to TWO
independent entity kinds -- the referenced equipment unit (for
maintenance scheduling) and the referenced batch (for shipment
coordination) -- rather than a single site/permit record.

This vertical has NO pre-existing `kotoba-lang/sawmilling`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic — pure functions in
`sawmilling.registry` (equipment/batch verification, shipment-volume
recompute, lumber-grade validation, moisture-content plausibility
validation) are re-verified independently by the governor, the same
"ground truth, not self-report" discipline established across prior
actors (most directly `cloud-itonami-isic-0220`'s `logging.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:sawmill-plant-operations-governor`, is grep-verified UNIQUE
fleet-wide (`gh search code "sawmill" --owner cloud-itonami`, zero
hits before this repo was created).

## Decision

### Decision 1: Self-contained domain logic (no external sawmilling capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
sawmilling vertical has NO pre-existing capability library to wrap.
The equipment/batch-verification / shipment-volume / grade / moisture-
content validation functions live as pure functions in
`sawmilling.registry` and are re-verified independently by
`sawmilling.governor` — the same "ground truth, not self-report"
discipline established across prior actors (most directly
`cloud-itonami-isic-0220`'s `logging.registry`).

### Decision 2: Coordination, not control — scope boundary at the back-office

This actor is **strictly back-office coordination** of sawmill plant
operations. It does NOT:
- Control saw blades, planers, or kiln equipment directly
- Make plant-safety or hazard decisions (exclusive to the human plant supervisor)
- Authorize or finalize a kiln schedule

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority —
it is a proposal-screening and documentation layer.

**CRITICAL SAFETY BOUNDARY**: sawmilling is a safety-critical domain
(saw-blade injury risk, kiln-fire risk, airborne wood-dust
respiratory/explosion hazard, heavy material handling). Safety-concern
flagging NEVER auto-commits. All safety concerns escalate immediately
to human review.

### Decision 3: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (equipment hazard, kiln-fire concern, dust
hazard, crew fatigue) ALWAYS escalates, never auto-commits. This is
not a "low-stakes proposal" — it is a circuit-breaker that must reach
human authority.

### Decision 4: Two independent verified/registered gates (equipment AND batch), not one

Unlike `cloud-itonami-isic-0220` (a single site/permit ground-truth
entity gating one op), this vertical has TWO entity kinds each gating
a different op: `:schedule-maintenance` independently verifies the
referenced **equipment** unit's own `:verified?`/`:registered?`
fields; `:coordinate-shipment` independently verifies the referenced
**batch**'s own `:verified?`/`:registered?` fields. Both are the same
"plant/batch record must be independently verified/registered before
any action" HARD invariant applied to the two distinct record kinds
this domain actually has. `:coordinate-shipment` additionally
independently recomputes whether a batch's own recorded
shipped-to-date volume plus the proposal's own claimed volume would
exceed the batch's own recorded production volume — never taken on
the advisor's self-report.

### Decision 5: HARD invariants (no override)

Four HARD governor invariants (elaborated into ten concrete checks in
`sawmilling.governor`, mirroring `cloud-itonami-isic-0220`'s own
elaboration of its HARD invariants into concrete checks) block
proposals and cannot be overridden by human approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's volume must independently recompute within the batch's own logged production volume
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct saw/planer/kiln-equipment control or kiln-schedule finalization is permanently blocked
4. The op allowlist is closed — `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Sawmill plant operations back-office now has a documented,
governed, auditable coordination layer that funnels all decisions
through independent validation before human approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world actuation requires human plant-
supervisor sign-off.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into ten concrete governor checks) protect against scope creep into
unauthorized equipment operation or kiln-schedule finalization. Safety
concerns are a circuit-breaker, not a threshold.

(+) Safety-critical discipline is explicit: safety-concern flagging
cannot be rate-limited, suppressed, or auto-decided by phase gate.
Human review is mandatory.

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation and kiln execution remain
human-controlled via external channels.

(-) No integration with real mill-management databases (equipment
telemetry, batch tracking, freight dispatch) — this is a standalone
coordinator blueprint.

## Verification

- `cloud-itonami-isic-1610`: `kbb -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `kbb -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-volume-exceeded, kiln-finalize-blocked,
  already-scheduled, invalid-grade, invalid-moisture-content).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) — no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `kbb -M:test` resolves offline inside the monorepo checkout.
