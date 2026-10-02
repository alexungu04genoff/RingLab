# Git-managed game data

These UTF-8 CSV files are the maintenance source for RingLab's catalog and base
stats. PostgreSQL remains the runtime source. Editing, pushing, deploying, and
restarting the backend do not import CSV content. Only the explicit `apply`
command writes catalog content.

## Baseline and evidence

The initial export is the final surviving state of unchanged Flyway V1–V33 on
main `ef0aeaf39dd58e31abd2943a69a066d3e2d75fcb`, reproduced in an isolated PostgreSQL 17
database. It contains:

| File/data | Records |
|---|---:|
| Racers | 52 |
| Machines | 62 |
| Machine parts | 174 |
| Gadgets | 79 |
| Maps | 44 |
| Game versions | 4 |
| Racer stats per version | 52 |
| Machine-part stats per version | 174 |
| Racer stats across all versions | 208 |
| Machine-part stats across all versions | 696 |

The versions are 1.2.0, 1.2.2, 1.3.1 and 1.4.1. V22/V24 copied current values into
older versions. Those rows remain **historical placeholders**, not verified
evidence that the values were unchanged in those patches. This extraction
preserves the current runtime result. See [the historical audit](../docs/stats-1.3.1-audit.md),
[catalog sources](../docs/game-data-sources.md) and [map sources](../docs/map-catalog-sources.md).

Eleven gadget `image_path` cells intentionally remain blank: the frontend's
existing `gadgetArtwork.ts` fallback supplies their artwork. CSV extraction does
not silently change the API's null values.

## Editing in Excel

Import CSV as UTF-8, comma-delimited text. Keep UUID/version columns as text,
dates in `YYYY-MM-DD`, and use `.` for decimal values. Export as UTF-8 CSV and
review the Git diff. Regional semicolon delimiters and decimal commas are rejected.

- Use normal CSV quoting: `"a, b"`, and `"a ""quoted"" name"`.
- UTF-8 BOM, LF and CRLF files are accepted. Quoted multiline fields are supported;
  embedded newlines are normalized to LF.
- The documented columns must occur exactly once; their order may vary. Unknown,
  duplicate or missing columns fail validation. Blank records are rejected.
- Blank numeric stat cells mean **UNKNOWN**; `0` means known zero. Do not use
  `NULL`, `N/A`, formulas, thousands separators or scientific notation.
- Decimal values are parsed directly into `BigDecimal`, without rounding.
- Blank optional text/type/cost fields are null. Names and dates are required.
- Keep current UUIDs. Assign a new UUID once when authoring a genuinely new
  identity; the importer never generates IDs or resolves identities by name.
- Keep rows ordered by UUID. Maps use catalog order; versions use release date.
  The resulting plan/token does not depend on row order or newline style.

## Files and exact columns

| File | Columns |
|---|---|
| `catalog/racers.csv` | `id,name,racing_type,image_path` |
| `catalog/machines.csv` | `id,name,racing_type,image_path` |
| `catalog/machine-parts.csv` | `id,source_machine_id,part_type` |
| `catalog/gadgets.csv` | `id,name,description,slot_cost,image_path` |
| `catalog/maps.csv` | `id,name,category,content_pack,image_path,catalog_order` |
| `catalog/game-versions.csv` | `id,version,released_at` |
| `versions/<version>/racer-stats.csv` | `racer_id,speed,acceleration,handling,power,boost` |
| `versions/<version>/machine-part-stats.csv` | `machine_part_id,speed,acceleration,handling,power,boost` |

Both stat files are required for every declared version. Undeclared version
directories are rejected. Each version maps to its explicit UUID in
`game-versions.csv`; file contents never choose a version by release-date fallback.

Racing types are `SPEED`, `ACCELERATION`, `HANDLING`, `POWER`, `BOOST`. Parts are
`FRONT`, `REAR`, `TIRE`. Map categories are `MAIN_COURSE`, `CROSSWORLD`.
Machine composition reuses Java `MachineComposition`: BOOST requires front/rear;
other known types require all three parts. An unknown machine type cannot be
imported as a complete setup. Racer type and gadget cost can remain unknown;
a known gadget cost must be 1–3.

## Supported changes

Allowed: new identities, new versions with complete snapshots, identical
reimports, and existing name/description/artwork changes. Map `content_pack`
is editable presentation metadata too.

Rejected: missing existing identities/rows, UUID replacement, existing racing
type or gadget-cost changes, part relationship/type changes, published stat
inserts/updates/removals, version-label/date changes, and map category/order
changes. Simultaneous name swaps/reuse are also rejected before SQL execution.
These restrictions avoid changing historical mechanics or introducing temporary
unique-constraint conflicts. No rows are ever deleted.

A new version must have one stat row for **every racer and part in the supplied
catalog**, even if all five values are blank. A catalog identity added later does
not require retroactively editing old snapshots. Offline validation checks file
presence and references; PLAN determines which versions are new and checks their
complete coverage against the target DB. Historical files retain their published
membership and values.

Use full independent snapshots. Copy files into a new version directory and edit
the new copy, adding its UUID/date to `game-versions.csv`. No inheritance/deltas.
Do not copy a number unless its continued value is supported; explicit unknown
cells are accepted. A new base-stat snapshot does not enable passive/scenario
rules or recommendations for that patch; their existing Java support is unchanged.

## Local commands (PowerShell, repository root)

Build the backend artifact after changing Java:

```powershell
mvn -f backend/pom.xml -DskipTests package
$importClasspath = 'backend/target/quarkus-app/app/*;backend/target/quarkus-app/lib/main/*'
java -cp $importClasspath dev.ringlab.GameDataImportMain validate game-data
```

`validate` requires neither PostgreSQL nor application/JWT/SMTP configuration.
The command never starts Quarkus, HTTP, Flyway or development fixtures.

For the repository's normal local PostgreSQL configuration:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ringlab'
$env:DB_USER = 'ringlab'
$env:DB_PASSWORD = 'ringlab'
java -cp $importClasspath dev.ringlab.GameDataImportMain plan game-data
```

Adjust the environment for your actual target. The command uses these three
variables explicitly; it does not load `.env` files or Quarkus datasource profiles.
Review every change, the write counts and `READY TO APPLY`. Then paste the exact
approval token from that plan:

```powershell
java -cp $importClasspath dev.ringlab.GameDataImportMain apply game-data --approve TOKEN_FROM_REVIEWED_PLAN
```

On Linux use `:` instead of `;` between classpath entries. Return codes: 0 means
success (including no-op), 2 means usage/validation/unsafe/stale-plan rejection,
1 means an operational failure. An unsafe plan writes nothing.

## Atomicity and approval

PLAN reads a consistent, read-only database snapshot. APPLY validates files again,
opens a transaction, takes one PostgreSQL advisory lock to serialize importer
processes, rereads catalog state and rebuilds the safe plan **before the first
content write**. Its SHA-256 approval token binds the complete desired dataset,
current catalog/stat state and importer policy. Changed files or database content
require another PLAN. The token is not a credential or a replacement for operator
review. Decimal scale-only differences (`1.0` versus `1.00`) are numerically equal.

The transaction inserts dependencies first and uses explicit presentation-only
UPDATEs. Exceptions roll back the transaction. A connection failure during COMMIT
can leave the outcome uncertain: run PLAN again before retrying. Identical
reimports perform zero content writes. No history table or new migration is needed.

The advisory lock coordinates importer processes only. Do not run manual catalog
SQL/content migrations concurrently. Existing application requests continue
reading PostgreSQL. Community rows, foreign keys and calculation code are untouched.

## Future production operation

See [production commands](../docs/production.md#explicit-game-data-import).
The reviewed backend image includes `/app/game-data` from the exact Git revision.
The `game-data` Compose service is behind the `tools` profile and only runs when
explicitly invoked. It has database-network access, no published port, no static
backend IP and no normal startup hook.

If APPLY fails, inspect its SQLSTATE and rerun PLAN. Do not remove catalog rows to
undo an import: builds may reference new identities. Successful changes require
a reviewed forward correction. Mechanics/historical corrections that this version
rejects need a focused implementation first.

## Deliberately deferred

Passive/scenario rule facts remain in Java. A follow-up can add CSV parsing into
typed rule records and compare complete current calculator outputs before changing
their source. Arithmetic, matching, stacking, conditions, coverage and search stay
Java. Retirement, versioned types/costs, provenance tables and historical revision
pinning are outside this importer.
