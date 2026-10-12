---
name: ringlab-game-data
description: Verify and release RingLab catalog or versioned stats/rules changes through the existing importer and parity workflow. Use for game-data revisions and PLAN/APPLY tasks, not ordinary UI or unrelated backend edits.
---

# RingLab game-data workflow

Use the existing Git-managed maintenance path. This skill does not implement a Registry, authorize a cutover or supply a future publication-activation procedure. If asked to activate Registry data later, inspect the integration that actually exists at that time before proceeding.

## Read only the relevant references

Paths below are relative to this skill directory; work from the RingLab repository root for maintenance commands.

- [Dataset guide](../../../game-data/README.md): canonical paths, supported changes, exact validate/plan/apply commands and approval-token semantics. Read for every data import/change.
- [Catalog ledger](../../../docs/game-data-sources.md), [map sources](../../../docs/map-catalog-sources.md), [passive sources](../../../docs/passive-gadget-sources.md) or [scenario evidence](../../../docs/scenario-preview-evidence.md): read the ledger for the facts being changed.
- [Historical stats audit](../../../docs/stats-1.3.1-audit.md) and [Phase 5B correction record](../../../docs/game-data-phase5b.md): consult for historical placeholders or protected-fact corrections.
- [Production import procedure](../../../docs/production.md#explicit-game-data-import): read only for a production import/release.

## Verify, plan, apply

1. Identify the target environment, game version, exact source revision and requested changes. Separate mutable presentation data, new identities/versions, and protected published numerical/mechanical facts. Preserve stable IDs, nullable unknowns, decimal precision and source/uncertainty evidence.
2. Check relevant ledgers against the proposed facts. Record conflicts without silently resolving them; historical copied values are not verified historical snapshots. Data availability does not expand Java calculation/recommendation permissions. Keep reviewed passive rules separate from scenario and unsupported effects.
3. Edit only the intended canonical files and supporting evidence. Run offline `validate` using the dataset guide. For importer/calculation code changes, run affected unit/parity tests; use real database tests when transaction, locking or persistence behavior changes. Review the complete diff.
4. For an import, run read-only PLAN against the intended database. Review every change, write totals and unsafe findings. Require `READY TO APPLY`; an unsafe plan is a rejection, not a reason to weaken validation. Protected corrections need explicit reviewed authorization and the documented forward-change mechanism, preserving earlier migrations.
5. Run APPLY only when the requested task authorizes the actual target/data mutation, using the exact token from the reviewed plan. Existing explicit authorization need not be requested again; a commit/push alone is insufficient. Changed files or database state require a fresh reviewed PLAN. Back up before risky persistent-data changes.
6. Preserve the importer lock/transaction boundary; do not run competing manual catalog SQL. If COMMIT outcome is uncertain, PLAN again before retrying. Do not delete/reload data to undo an import.
7. After APPLY, require a no-op PLAN and inspect affected API/build/stat behavior. For a scoped production release, follow immutable image/commit and smoke-check requirements in the production procedure.

Report the revision/version, evidence or remaining uncertainty, validation/parity results, plan/apply outcome and whether runtime data actually changed. Pushing, deploying or restarting alone does not import CSV content.
