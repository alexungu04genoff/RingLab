# GET /api/builds performance baseline

## Scope and conclusion

This is a repeatable development benchmark of the current RingLab build-browsing design at commit
`2681b28`, measured on 2026-09-13. It is not a scientific microbenchmark and it does not justify a
production capacity claim. Its purpose is narrower: make the cost of application-side global ranking
before pagination visible, preserve the current ranking semantics, and provide evidence for a later
optimization task.

No production code, ranking rule, REST shape, or Flyway seed was changed for this baseline. The
manual benchmark lives in
`backend/src/test/java/dev/ringlab/performance/BuildBrowsePerformanceBenchmark.java`. Its class name
does not end in `Test`, so ordinary Maven test discovery does not run it.

The principal result is that unfiltered work grows with the matching candidate count, not the 12-row
page size. At 5,000 candidates, the median service time was 502-580 ms for page 0 and the request used
363-373 SQL statements after response enrichment. A later page did the same 315 service statements as
page 0. NEWEST unnecessarily fetched all 5,000 vote summaries; a test-only counterfactual that omitted
that query reduced its median service time from 579.781 ms to 396.859 ms on this machine.

## Architecture measured

The measured path is:

```text
GET /api/builds
  -> BuildRestResource validates query parameters
  -> BuildService.list
       -> BuildRepository.search: PostgreSQL filters, returns every matching Build
       -> BuildDbMapper initializes every candidate's ordered gadget collection
       -> VoteRepository.summaries: one grouped query for every candidate ID
       -> BuildRanking: NEWEST, SCORE, or BEST_RATED in Java
       -> subList: pagination after global ranking
  -> BuildRestResource.response for each returned build
       -> author, racer, three parts and their source machines
       -> version, gadgets, vote summary, and optional remix source
  -> JSON response
```

The persistence adapter does not contain Wilson score, sign-bucket, tie-break, or BEST_RATED policy.
This baseline does not propose moving those rules into persistence.

An important measured detail is that candidate mapping initializes `BuildDbEntity.gadgetIds` for
every match. Hibernate batch-fetches those collections in groups of about 16 in this environment.
Consequently the service statement count for 5,000 unfiltered candidates was 315: one candidate
query, 313 collection-fetch statements, and one bulk vote-summary statement. It was not 5,002
statements, but the collection work still scales linearly with the candidate set and is unrelated to
ranking.

## Environment

| Component | Measured environment |
|---|---|
| Host | Windows 11 Pro 10.0.22621 |
| CPU | AMD Ryzen 5 1600X, 6 physical / 12 logical cores |
| Memory | 11.9 GiB |
| JVM | Eclipse Temurin OpenJDK 21.0.12.1, 64-bit HotSpot |
| Maven | 3.9.16 |
| Application | Quarkus 3.27.2, JVM test profile, loopback HTTP on port 8081 |
| Database | PostgreSQL 17.11 in the project's Docker container, loopback JDBC |
| Schema | Flyway V1-V14, Hibernate schema validation |
| Instrumentation | Hibernate Statistics; Maven's normal JaCoCo agent was active |

The application and database shared one development workstation. No other load was intentionally
generated. The benchmark raised only the test-profile build-list rate limit so repeated requests were
not rejected.

## Dataset and methodology

The benchmark used a dedicated database named exactly `ringlab_benchmark`. It refuses to truncate any
other database. Flyway supplied only the normal catalog; the benchmark generated users, builds, gadget
relations, and votes directly in the disposable database and removed them after the run. It did not
modify or use production seed migrations.

Three exact build counts were measured: 60, 500, and 5,000. Each build had two gadgets. Data was
distributed deterministically over 20 authors, five racers, five stock-machine part sets, and four
game versions. Every tenth title contained `Search Target`; every tenth eligible build remixed its
immediate predecessor. Vote patterns included high-confidence positive, small positive, neutral,
negative, and unvoted builds so SCORE and BEST_RATED produced meaningful and different global orders.
The largest requested scale was practical and was measured in full.

For each dataset and scenario:

- two warm-up calls ran before seven recorded calls;
- the table reports the median of those seven calls;
- service latency measured `BuildService.list` in a fresh JTA transaction;
- end-to-end latency measured an actual loopback HTTP request, including filters, REST conversion,
  JSON serialization, response transfer, status checking, and JSON parsing in the harness;
- Hibernate Statistics was cleared for each recorded call and supplied prepared-statement counts;
- the HTTP item IDs and order were asserted equal to the corresponding direct service result, and
  `items`, `total`, page, and size were checked;
- bulk vote-query input size equals the matching candidate count, including NEWEST;
- logical enrichment calls were counted from the returned response fields, while Hibernate entity
  loads show where the request persistence context reused repeated catalog identities.

The later-page scenario was BEST_RATED page 4, size 12. Search was the literal substring
`Search Target`. Filters selected one of the deterministic racers, source machines, or game versions.

## Results

All latencies are milliseconds. `Service SQL` covers filtering, candidate gadget initialization,
bulk vote facts, ranking, and pagination. `HTTP SQL` covers the complete request. `Enrich SQL` is the
difference and therefore captures page response assembly for the same deterministic result shape.

| Builds | Scenario | Candidates / total | Returned | Service median | HTTP median | Service SQL | HTTP SQL | Enrich SQL |
|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 60 | newest, page 0 | 60 | 12 | 34.520 | 166.805 | 6 | 64 | 58 |
| 60 | score, page 0 | 60 | 12 | 31.637 | 135.407 | 6 | 59 | 53 |
| 60 | rated, page 0 | 60 | 12 | 20.720 | 125.145 | 6 | 59 | 53 |
| 60 | racer-filtered rated | 12 | 12 | 12.276 | 92.615 | 3 | 40 | 37 |
| 60 | machine-filtered rated | 12 | 12 | 12.500 | 113.185 | 3 | 40 | 37 |
| 60 | version-filtered rated | 15 | 12 | 14.293 | 104.308 | 3 | 53 | 50 |
| 60 | title search, rated | 6 | 6 | 11.517 | 67.667 | 3 | 30 | 27 |
| 60 | rated, page 4 | 60 | 12 | 17.265 | 119.860 | 6 | 60 | 54 |
| 500 | newest, page 0 | 500 | 12 | 91.390 | 184.588 | 34 | 92 | 58 |
| 500 | score, page 0 | 500 | 12 | 69.448 | 124.785 | 34 | 82 | 48 |
| 500 | rated, page 0 | 500 | 12 | 56.895 | 132.534 | 34 | 82 | 48 |
| 500 | racer-filtered rated | 100 | 12 | 21.310 | 105.198 | 9 | 54 | 45 |
| 500 | machine-filtered rated | 100 | 12 | 22.626 | 85.356 | 9 | 54 | 45 |
| 500 | version-filtered rated | 125 | 12 | 28.748 | 100.110 | 10 | 62 | 52 |
| 500 | title search, rated | 50 | 12 | 15.260 | 88.247 | 6 | 51 | 45 |
| 500 | rated, page 4 | 500 | 12 | 66.554 | 130.226 | 34 | 82 | 48 |
| 5,000 | newest, page 0 | 5,000 | 12 | 579.781 | 604.965 | 315 | 373 | 58 |
| 5,000 | score, page 0 | 5,000 | 12 | 502.267 | 642.074 | 315 | 363 | 48 |
| 5,000 | rated, page 0 | 5,000 | 12 | 520.096 | 594.656 | 315 | 363 | 48 |
| 5,000 | racer-filtered rated | 1,000 | 12 | 98.246 | 166.252 | 65 | 110 | 45 |
| 5,000 | machine-filtered rated | 1,000 | 12 | 109.467 | 158.150 | 65 | 110 | 45 |
| 5,000 | version-filtered rated | 1,250 | 12 | 128.317 | 201.923 | 81 | 133 | 52 |
| 5,000 | title search, rated | 500 | 12 | 58.906 | 125.659 | 34 | 79 | 45 |
| 5,000 | rated, page 4 | 5,000 | 12 | 457.757 | 608.889 | 315 | 363 | 48 |

The 60-row HTTP medians contain a large fixed REST/serialization/instrumentation component and should
not be compared as if sub-millisecond differences were meaningful. At 5,000 rows the candidate phase
is dominant enough for the scaling direction to be clear.

### NEWEST vote-summary impact

Production NEWEST executed the same all-candidate bulk vote query as SCORE and BEST_RATED even though
its comparator never reads a vote. The counterfactual below was implemented only inside the benchmark
harness by calling the same repository search and canonical NEWEST comparator with an empty summary
map. Production code was not changed.

| Candidates | Current NEWEST service | Without vote query | Median difference | Current SQL | Without-vote SQL |
|---:|---:|---:|---:|---:|---:|
| 60 | 34.520 | 14.487 | 20.033 | 6 | 5 |
| 500 | 91.390 | 58.209 | 33.180 | 34 | 33 |
| 5,000 | 579.781 | 396.859 | 182.923 | 315 | 314 |

The SQL-count saving is only one statement, but its parameter collection and grouped vote work scale
with all matching IDs. At 5,000 candidates it represented 31.5% of the measured service median on
this run. This counterfactual is evidence for a focused future change, not a production benchmark of
an implemented optimization.

### Response assembly fan-out

Every full 12-item page made these logical calls before persistence-context reuse:

- 12 author lookups;
- 12 racer lookups;
- 36 part lookups and 36 corresponding source-machine lookups;
- 24 gadget lookups;
- 12 version lookups in this fully versioned synthetic dataset;
- 12 individual vote-summary queries, despite the same summaries having just been bulk-fetched for
  candidate ranking;
- one optional remix lookup for each remixed item.

For the 5,000-row unfiltered pages, actual distinct entity loads during enrichment were:

| Page result | Author | Racer | Part | Source machine | Gadget | Version | Per-build vote SQL | Total enrich SQL |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| newest page 0 | 12 | 5 | 15 | 5 | 5 | 4 | 12 | 58 |
| score/rated page 0 | 5 | 5 | 15 | 5 | 5 | 1 | 12 | 48 |
| rated page 4 | 5 | 5 | 15 | 5 | 5 | 1 | 12 | 48 |

Those columns sum to the measured enrichment SQL because the relevant unfiltered remix sources were
already in the all-candidate persistence context. Filtered pages exposed a second confirmed form of
fan-out: when a remix source did not match the filter, resolving it loaded both the source build and
its gadget collection. Twelve such remixes added 24 SQL statements. This explains the 45-statement
enrichment result for the 1,000-candidate racer and machine filters: 9 distinct author/catalog entity
loads + 12 individual vote summaries + 24 remix-related statements.

The response assembler therefore has confirmed N+1/fan-out behavior. First-level identity reuse
prevents every logical lookup from becoming SQL when page rows share catalog records, but query count
still depends on page composition and grows with page size. The repeated individual vote summaries
are unconditionally redundant for list responses.

## Candidate and memory growth observations

At page size 12, unfiltered service SQL grew from 6 at 60 candidates, to 34 at 500, to 315 at 5,000.
The corresponding BEST_RATED page-0 service medians grew from 20.720 ms, to 56.895 ms, to 520.096 ms.
Filtering to 1,000 candidates reduced BEST_RATED service time to 98-109 ms; title search reduced the
5,000-row dataset to 500 candidates and 58.906 ms. A later page did not reduce candidate work: page 4
still used 315 service statements and ranked all 5,000 matches.

No heap profiler was attached, so this report does not claim byte-accurate allocation figures. Code
and runtime counters confirm linear candidate retention: one domain `Build` plus its copied gadget
list per match, a candidate-ID list passed to the vote query, a vote-summary map entry for every voted
candidate, and a second ranked list of candidate references. Returned response size remains bounded
at 12, but ranking-phase live data is O(matching candidates). This is a memory-growth observation, not
a measured heap-capacity limit.

## Bottlenecks and tradeoffs

1. **Candidate materialization dominates at larger scales.** Exact global application ranking loads
   every matching build. Candidate mapping also initializes gadget collections that none of the three
   ranking comparators use. Hibernate batching softens the query count but does not remove O(N) data,
   mapping, or allocation.
2. **NEWEST performs irrelevant vote work.** It sends every candidate ID to the grouped vote query and
   constructs summaries that its comparator does not read. The 5,000-row test-only omission saved a
   median 182.923 ms.
3. **List response votes are queried twice.** Ranking bulk-fetches vote facts for all candidates, then
   response assembly issues one summary query for every returned item instead of reusing page facts.
4. **Response enrichment is a bounded but real fan-out.** A normal 12-item unfiltered page added 48-58
   SQL statements. Shared identities benefit from the persistence context; filtered remix sources can
   cost two statements each because mapping also initializes their gadgets.
5. **Pagination currently bounds only output.** It does not bound retrieval, vote aggregation, sorting,
   gadget hydration, or the main in-memory structures.

## Evidence-backed optimization candidates

These are proposals for a separate implementation task. None is implemented here.

| Rank | Candidate | Expected benefit | Complexity | Architectural risk | Evidence |
|---:|---|---|---|---|---|
| 1 | Skip bulk vote summaries for NEWEST | High for unfiltered large sets | Low | Low | Saved 182.923 ms and one 5,000-ID grouped query in the test-only counterfactual; comparator semantics need no votes. |
| 2 | Return/reuse the ranking summary for page items | Moderate, stable | Low-medium | Low | Removes 12 redundant vote queries from every full page while keeping raw vote facts and ranking in application/domain code. |
| 3 | Use a focused browse candidate projection that omits gadget collections, then load gadgets only for the page | High at larger sets | Medium | Medium | 313 of 315 service statements at 5,000 candidates were batched gadget-collection initialization; gadgets do not affect ranking. |
| 4 | Batch page response enrichment by distinct author/catalog/remix IDs | Moderate, especially at demo scale | Medium | Low-medium | Page assembly added 48-58 statements unfiltered and 45-52 in several filtered cases. |
| 5 | Investigate smarter exact candidate-fact retrieval for SCORE/BEST_RATED without moving policy into persistence | Potentially high | High | High | All candidates and vote facts remain necessary to preserve exact global application ranking unless a carefully proven top-k/fact strategy is introduced. |

The first two are the smallest low-risk changes. A browse/read projection is likely more valuable than
a generic repository framework because it directly removes measured irrelevant hydration. Any smarter
large-scale retrieval must prove unchanged sign buckets, Wilson calculation, score/creation/UUID ties,
and pagination before adoption.

## Suspicious observations that were insignificant or inconclusive

- SCORE and BEST_RATED have different comparator complexity, but their 5,000-row medians were close
  relative to run-to-run development noise (502.267 vs 520.096 ms service). Database/materialization
  work was much larger than a demonstrated Wilson-computation cost.
- The source-machine filter uses a subquery, but at the same 1,000-candidate selectivity its median was
  close to the racer filter (109.467 vs 98.246 ms service; 158.150 vs 166.252 ms HTTP). This run does
  not justify query-specific optimization there.
- Page 4 sometimes timed slightly faster or slower than page 0, but its candidate and SQL counts were
  identical. The variation is noise; there is no evidence that later pagination reduces work.
- First-level persistence-context reuse made repeated catalog identities cheaper than the raw logical
  call count suggests. The lookup structure is still fan-out, but shared catalog IDs were not the main
  5,000-candidate bottleneck.

## Correctness checks

For every recorded scenario, the harness compared ordered HTTP response IDs with the ordered direct
`BuildService` result and checked total and returned counts. All assertions passed. The benchmark used
the production `BuildRanking`, `WilsonScore`, `BuildSort`, repository adapters, REST resource, and JSON
DTOs unchanged. It did not change BEST_RATED sign buckets, Wilson score, tie-breaks, pagination, or
response shape.

## Limitations

- This is a single-user, sequential development benchmark on one machine; it does not measure
  throughput, lock contention, pool saturation, or tail latency under concurrency.
- PostgreSQL and Quarkus shared the host, HTTP was loopback-only, and Docker/Windows scheduling can
  affect individual medians.
- JaCoCo and Hibernate Statistics add overhead. Absolute production latencies will differ.
- Seven recorded samples are enough for a useful local median but not a statistical performance study.
- Synthetic distributions are deterministic and intentionally varied, not a claim about real user,
  vote, filter, remix, or gadget distributions.
- Only page size 12 was measured. Response fan-out will differ with page size and page composition.
- No allocation profiler, GC log analysis, database `EXPLAIN (ANALYZE, BUFFERS)`, or network load tool
  was used. Memory conclusions are structural scaling observations only.
- Cold startup and first-request costs were excluded by warm-up.

## Reproduction

With the project PostgreSQL container running, create the dedicated disposable database once:

```powershell
docker exec ringlab-postgres-1 createdb -U ringlab ringlab_benchmark
```

From the repository root, run only the manual benchmark:

```powershell
mvn -f backend/pom.xml `
  "-Dtest=BuildBrowsePerformanceBenchmark" `
  "-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_benchmark" `
  "-Dquarkus.datasource.username=ringlab" `
  "-Dquarkus.datasource.password=ringlab" `
  "-Dquarkus.datasource.devservices.enabled=false" test
```

The output lines prefixed `PERF_RESULT`, `PERF_FANOUT`, and `PERF_NEWEST_IMPACT` are the machine-readable
measurements. Generated application rows are truncated in `@AfterAll`; the migrated catalog remains.
The benchmark refuses to modify a database whose name is not exactly `ringlab_benchmark`. Remove the
whole disposable database when it is no longer needed:

```powershell
docker exec ringlab-postgres-1 dropdb -U ringlab ringlab_benchmark
```
