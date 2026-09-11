# cloud-itonami-isic-1610: Sawmilling and planing of wood

Open Business Blueprint for **ISIC Rev.5 1610**: sawmilling and planing of wood — an autonomous "actor" (LLM advisor behind an independent Governor, langgraph-clj StateGraph, append-only audit ledger) that coordinates back-office sawmill **plant operations**: production-batch data logging (lumber grade/volume/moisture-content), saw-blade/planer/kiln maintenance scheduling, safety-concern flagging, and outbound lumber shipment coordination.

This repository designs a forkable OSS business for sawmill plant
operations: run by a qualified operator so a sawmill keeps its own
operating records instead of renting a closed SaaS.

## What this actor does

Proposes **plant operations coordination**, not equipment operation:
- `:log-production-batch` — lumber-grade/volume/moisture-content data logging (administrative, not an operational decision)
- `:schedule-maintenance` — saw-blade/planer/kiln maintenance scheduling proposal
- `:flag-safety-concern` — surface an equipment/kiln-fire/dust-hazard concern (always escalates)
- `:coordinate-shipment` — outbound lumber shipment coordination proposal

## What this actor does NOT do

**CRITICAL SCOPE BOUNDARY — this is a safety-critical domain** (heavy
saw/planer equipment, kiln fire risk, airborne wood-dust hazard):

- Does NOT control saw blades, planers, or kiln equipment directly
- Does NOT make plant-safety or hazard decisions (that's the plant supervisor's exclusive human authority)
- Does NOT authorize or finalize a kiln schedule (human plant supervisor decides)
- ONLY proposes/coordinates operations back-office; all actuation requires explicit human approval
- Safety-concern flagging ALWAYS escalates — never auto-decided, no confidence threshold or phase below escalation

## Architecture

Classic governed-actor pattern (`sawmilling.operation/build`, a langgraph-clj StateGraph):
1. **`sawmilling.advisor`** (sealed intelligence node, `SawmillAdvisor`): proposes decisions only, never commits
2. **`sawmilling.governor`** (independent, `Sawmill Plant Operations Governor`): validates against domain rules, re-derived from `sawmilling.registry`'s pure functions and `sawmilling.store`'s SSoT -- never trusts the advisor's own self-report
   - HARD invariants (always `:hold`, no override):
     - Plant/batch record must be independently verified/registered (`:verified?` AND `:registered?`) before any action is taken against it (equipment before maintenance scheduling, batch before shipment coordination)
     - The request's own `:effect` must be `:propose` (never a direct-write bypass)
     - `:op` must be in the closed four-op allowlist
     - The proposal's own `:effect` must be one of the four propose-shaped effects (no direct saw/planer/kiln-equipment control)
     - Finalizing a kiln schedule (`:finalize? true`) is a PERMANENT, unconditional block
     - A shipment may not push a batch's own recorded shipped volume past its own logged production volume (independently recomputed)
     - No double-scheduling the same maintenance record
     - No fabricated `:grade` value on a production-batch patch
     - No physically implausible `:moisture-content-percent` value on a production-batch patch
   - ESCALATE (always human sign-off, overridable by a human):
     - `:flag-safety-concern` always escalates, regardless of confidence
     - Low-confidence proposals
3. **`sawmilling.phase`** (Phase 0->3 rollout): `:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` are NEVER in any phase's `:auto` set (permanent, matching the governor's own posture); only `:log-production-batch` may auto-commit at phase 3 when clean
4. **`sawmilling.store`** (append-only audit ledger + SSoT): a single `MemStore` backend behind a `Store` protocol (see ns docstring for why a second Datomic-backed backend is out of scope for this build)

## Development

```bash
# Run tests (top-level deps.edn already pins langgraph+langchain local/root)
kbb -M:test

# Run tests via the workspace :dev override alias (equivalent, kept for sibling-repo parity)
kbb -M:dev:test

# Run the demo
kbb -M:dev:run

# Lint
kbb -M:lint
```

## Status

`:implemented` — `governor.cljc`/`store.cljc`/`advisor.cljc`/`registry.cljc` + `deps.edn` complete the module set; tests green, demo runnable, langgraph-clj integration verified.

## License

AGPL-3.0-or-later
