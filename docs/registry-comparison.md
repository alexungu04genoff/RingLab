# Read-only Registry comparison (Phase D)

RingLab still serves PostgreSQL exclusively. Registry comparison is an explicitly
invoked, one-shot operator command. It does not boot Quarkus, run Flyway, expose an
HTTP endpoint, install a startup observer, poll in the background, or change data.
No production deployment or activation is part of this phase.

## Architecture

`RegistryComparisonMain` wires the inbound `RegistryComparisonCommand` through
`CompareRegistryUseCase` to `RegistryComparisonService`. The service has only two
outbound capabilities: `RegistryPublicationSource` and `GameDataSnapshotReader`.
It cannot call APPLY. `GameDataImportDbAdapter` implements the latter using its
existing read-only, repeatable-read JDBC transaction followed by rollback.

`RegistryHttpAdapter` performs GETs to `/api/v1/publications/latest/meta`, then to
the exact `/api/v1/publications/{revision}` advertised in that metadata. A changing
latest head therefore cannot mix revisions. Redirects are rejected. Requests use
identity encoding, bounded connect/whole-response timeouts, a 256 KiB metadata
limit, and a configurable publication size limit. Remote error bodies and URLs
are not printed in diagnostics.

Strict UTF-8/JSON parsing rejects duplicate keys (including escaped equivalents),
invalid Unicode, BOMs, floating-point tokens, unsafe integers and trailing values.
The pinned schema and semantic verifier check closed objects, explicit nulls,
identities, machine composition, snapshot/gap partitions, provenance, source
relationships, rule positions and normalized ordering before checking the
publication checksum. The client independently computes the dataset fingerprint,
compares every advertised metadata field, and checks representation ETags.
Metadata is validated with the publication envelope's identical field definitions
and parent/timestamp semantics plus the required dataset fingerprint; no schema
references are retrieved over the network.

The client caches only one fully verified publication/metadata pair in memory.
Repeated calls send both `If-None-Match` headers and accept 304 only with the
corresponding verified representation and matching strong ETag. An empty Registry
clears that cache. Errors never return stale data as a current comparison. A
revision that changes or a latest revision that regresses within a client session
is rejected. A fresh command has no cache or historical revision memory. There is
no disk cache, retry loop, offline fallback or authenticity signature; the configured
origin and TLS remain the trust boundary. Local development may use loopback HTTP.

The lossless JDK-only `Publication` model retains exact decimal strings, every
provenance claim and evidence order, explicit gaps, nullable fields and rule
positions. Its lists are immutable. `RegistryProjection` builds a separate
`GameDataSet` for comparison, using `BigDecimal` without rounding and leaving
missing rows absent. It never interprets non-passive zero vectors as zero effects.
Rule sources retain their effect identities and ordered positions.

The application directly reuses `GameDataSetValidator`, `GameDataRuleValidator`
and `GameDataImportPlanner`. `GameDataImportService` was inspected but is not used
as the consumer boundary because it also exposes APPLY. The planner's protected
facts, deletion checks, name-reuse checks and new-snapshot completeness policy
remain unchanged. Its approval token is neither exposed nor used by this command.

## Operator command

From the repository root, build once after Java changes:

```powershell
mvn -f backend/pom.xml -DskipTests package
$registryClasspath = 'backend/target/quarkus-app/app/*;backend/target/quarkus-app/lib/main/*;backend/target/quarkus-app/lib/boot/*'
$env:REGISTRY_URL = 'http://localhost:8181'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ringlab'
$env:DB_USER = 'ringlab'
# Supply DB_PASSWORD through your normal local environment; never commit credentials.
java -cp $registryClasspath dev.ringlab.RegistryComparisonMain compare backend/target/registry-comparison.json
```

Linux classpath entries use `:` instead of `;`. The command reads environment
variables explicitly; it does not load `.env` files or Quarkus profiles.
`REGISTRY_URL` must be an HTTP(S) origin without credentials, path, query or fragment.
Optional `REGISTRY_TIMEOUT_SECONDS` defaults to 15 (1–120 allowed), and
`REGISTRY_MAX_BYTES` defaults to 16777216 (16 MiB). PostgreSQL connection and socket
timeouts are 10 and 30 seconds. A SELECT-only database role can be used; no schema
or import privileges are required. The output path must be new; existing files
are never overwritten. Reports contain the full source data and should be kept
outside Git unless deliberately reviewed as fixtures.

Exit codes:

- `0`: completed projection-compatible comparison, or `NO_PUBLICATIONS`.
- `2`: invalid arguments/configuration, existing output file, protected difference
  or incompatible mapping. A completed blocked comparison still writes its report.
- `1`: network, timeout, HTTP/contract verification, database or report-write failure.

An empty Registry or invalid publication causes no PostgreSQL read. A valid
publication is compared with one consistent database snapshot; concurrent later
database changes require a fresh comparison. No result is an activation approval.

## Reading the report

The console shows Registry revision/schema, both sets of game versions, publication
checksum, dataset fingerprint, comparison fingerprints and difference counts.
The JSON artifact contains all differences, protected changes, incompatible
mappings, uncertainty observations and the complete verified publication.

Three digest purposes remain separate:

- Registry publication checksum: SHA-256 of canonical publication with only the
  checksum property omitted, as defined by the frozen v1 contract.
- Registry dataset fingerprint: SHA-256 of its canonical normalized dataset,
  including provenance, gaps and ordering.
- PostgreSQL/projected comparison fingerprints: existing importer field encoding
  under the independent `ringlab-comparison-snapshot-v1` prefix. Equal fingerprints
  mean represented facts match, not that provenance is represented or verified.

None is the CSV importer's `ringlab-import-policy-v2` PLAN/APPLY approval token.

`BLOCKED` means existing safety policy or a lossless mapping constraint fails.
`PROJECTION_COMPATIBLE_REQUIRES_RETENTION_DESIGN` means represented facts pass the
current planner, but Phase E must still design retention and activation checks.
There is deliberately no “ready to activate” state.

PostgreSQL has no storage for Registry publication identity, provenance mappings,
claims/evidence or explicit gap explanations. The report retains them; they cannot
be silently discarded during future activation. Historical missing rows remain
different from rows containing five nulls. The importer rejects explicit gaps in
a **new** version because its existing policy requires a stat row for every
identity. Registry stacking groups can also be broader than RingLab's historical
tuner policy, and a Registry-valid empty rule set fails RingLab's rule validator.
These are reported incompatibilities, not reasons to weaken either contract.

Additional checks identify PostgreSQL string-length/NUL and
[NUMERIC storage limits](https://www.postgresql.org/docs/17/datatype-numeric.html)
without truncation or rounding. Unsupported/conditional/non-stat rules,
unknown values, incomplete mappings and non-reviewed claims remain explicit.
No rule label, evidence URL or new version changes calculation/recommendation
permissions. The comparison does not validate that an artwork file exists locally,
fetch evidence URLs, independently establish provenance truth, or compare source
claims against a PostgreSQL provenance history that does not exist.

## Frozen reference and baseline differences

Consumer schema, reference validation/canonicalization code and golden tests were
ported from `RingLab-GameData` commit
`8422fb49c30546d83ffda81ee5fd3aad1f4a334c`. The authoritative repository remains the
source of truth. Consumer changes remove producer/draft helpers and CDI registration,
add metadata verification, and make publication collections immutable. Future
contract updates must be reviewed explicitly; do not regenerate fixtures to make
tests pass. See the bundled [reference manifest](../backend/src/main/resources/registry/v1/README.md).

The baseline was extracted from RingLab
`1bb9459fa8caa02e3f536054cb4d61529739dab6`. Its projection matches this checkout's
canonical CSV dataset exactly: 53 racers, 63 machines, 177 parts, 117 gadgets,
44 maps, four game versions, and one rule set. Its 15 explicit historical gaps
remain gaps. This does not imply parity with an arbitrary PostgreSQL database.
An isolated database containing migrations through V37 has 117 candidate gadget
presentation updates, with no additions or protected differences. Those later
CSV artwork/acquisition updates are deliberately not applied by comparison.

The read-only development-database comparison on 2026-10-12 found **zero additions,
presentation updates, protected differences or incompatible mappings**. Its
PostgreSQL and projected comparison fingerprints both were
`4b9198164377262bbade5b0473af65a65713ffb0f31d587889a8d4a3a1b22082`.
That observed parity does not erase the provenance/gap persistence limitations,
and is not a statement about production data.

## Verification

Focused pure tests (no database required):

```powershell
mvn -f backend/pom.xml "-Dtest=ContractTest,RegistryHttpAdapterTest,RegistryComparisonServiceTest,RegistryComparisonMainTest,ArchitectureTest" test
```

`RegistryComparisonIntegrationTest` uses a disposable migrated PostgreSQL schema,
observes every JDBC execution, rejects any non-SELECT execution/commit, verifies
read-only repeatable-read setup and rollback, and compares every game-data row
before and after. It also checks PostgreSQL's own read-only transaction rejection.
Run it with Docker/Dev Services or the dedicated test database configuration in
[README tests](../README.md#tests). Full `mvn verify` retains existing backend
coverage gates and packaged API checks. Never aim these suites at development or
production data. No frontend files or frontend testing requirements change.

Recorded local verification on 2026-10-12: the full backend run passed 644 tests
and 22 packaged API checks. After final standalone-command hardening, 94 focused,
architecture and PostgreSQL integration checks plus all 22 packaged API checks
passed again, with the existing coverage gates enforced. Both packaged runs used
an unavailable `REGISTRY_URL`. CLI smoke checks returned 0 for the matching frozen
fixture and empty Registry, and 1 for an outage; the latter two used an unreachable
database URL, confirming that neither case opens a PostgreSQL connection.
