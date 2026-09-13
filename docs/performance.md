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

## Optimization Pass 1

Measured on 2026-09-13 with the same `BuildBrowsePerformanceBenchmark`, disposable database,
datasets, scenarios, two warm-ups, seven recorded iterations, and development environment described
above. The original baseline tables remain unchanged.

This pass made two application-level changes:

1. NEWEST now ranks candidates with the canonical NEWEST comparator without retrieving candidate-wide
   vote summaries. After pagination it bulk-fetches summaries only for the returned page IDs so the
   response still displays votes.
2. SCORE and BEST_RATED retain their candidate-wide bulk summaries for exact ranking, but BuildService
   now returns the relevant page summaries with the page. BuildRestResource uses those facts instead
   of issuing one vote-summary query per returned build.

### Pass 1 rerun

All latencies are milliseconds. These are the three unfiltered page-0 scenarios from the complete
60, 500, and 5,000-build rerun.

| Builds | Scenario | Service median | HTTP median | Service SQL | HTTP SQL |
|---:|---|---:|---:|---:|---:|
| 60 | newest | 30.317 | 137.059 | 6 | 52 |
| 60 | score | 23.868 | 106.105 | 6 | 47 |
| 60 | rated | 18.408 | 112.744 | 6 | 47 |
| 500 | newest | 74.237 | 129.326 | 34 | 80 |
| 500 | score | 63.322 | 115.492 | 34 | 70 |
| 500 | rated | 58.171 | 110.716 | 34 | 70 |
| 5,000 | newest | 508.365 | 649.014 | 315 | 361 |
| 5,000 | score | 855.950 | 560.504 | 315 | 351 |
| 5,000 | rated | 444.796 | 482.667 | 315 | 351 |

### Baseline comparison at 5,000 builds

Positive percentages mean a lower median; negative percentages mean the rerun was slower. The
development benchmark measures the service and HTTP paths separately, so their medians can vary
independently. In particular, the SCORE service rerun was an outlier in the slower direction while
its HTTP median improved. SQL counts and query scope are the more deterministic evidence for this
pass.

| Scenario | Service before | Service after | Service improvement | HTTP before | HTTP after | HTTP improvement | Service SQL before / after | HTTP SQL before / after |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| NEWEST | 579.781 | 508.365 | 12.3% | 604.965 | 649.014 | -7.3% | 315 / 315 | 373 / 361 |
| SCORE | 502.267 | 855.950 | -70.4% | 642.074 | 560.504 | 12.7% | 315 / 315 | 363 / 351 |
| BEST_RATED | 520.096 | 444.796 | 14.5% | 594.656 | 482.667 | 18.8% | 315 / 315 | 363 / 351 |

Every full 12-item HTTP response used 12 fewer SQL statements. SCORE and BEST_RATED still use one
candidate-wide grouped summary query because exact ranking requires those facts. NEWEST now uses one
page-wide grouped summary query for 12 IDs instead of one candidate-wide grouped query for 5,000 IDs;
therefore its service SQL count remains 315 even though the work performed by that statement is much
smaller. Its 5,000-build service median improved by 71.416 ms in this rerun, but the HTTP median
regressed by 44.049 ms, which illustrates the noise limits of this single-workstation benchmark.

Performance is not solved. Candidate mapping still initializes gadget collections for every matching
build. At 5,000 unfiltered candidates those batched collection fetches remain 313 of the 315 service
statements and are expected to remain the dominant large-set cost. The browse candidate
projection/page-only gadget hydration optimization remains deliberately unimplemented.

## Optimization Pass 2

Measured on 2026-09-13 with the same `BuildBrowsePerformanceBenchmark`, disposable database,
datasets, scenarios, two warm-ups, seven recorded iterations, and development environment described
above. The baseline and Pass 1 values remain unchanged.

This pass replaces full candidate materialization with a two-phase browse read. `BuildDbAdapter`
projects only candidate ID and creation time while retaining every existing database filter.
`BuildService` performs canonical global ranking and pagination over those facts, bulk-hydrates only
the selected page, and restores the ranked ID order independently of database result order. SCORE and
BEST_RATED still retrieve candidate-wide raw vote summaries; NEWEST still retrieves summaries only
for the page.

### Pass 2 complete rerun

All latencies are milliseconds. Service SQL now consists of candidate projection, optional ranking
vote facts or page vote facts, page entity hydration, and one batched page gadget-collection fetch.

| Builds | Scenario | Candidates / total | Returned | Service median | HTTP median | Service SQL | HTTP SQL |
|---:|---|---:|---:|---:|---:|---:|---:|
| 60 | newest, page 0 | 60 | 12 | 24.462 | 137.121 | 4 | 50 |
| 60 | score, page 0 | 60 | 12 | 18.151 | 103.186 | 4 | 49 |
| 60 | rated, page 0 | 60 | 12 | 21.007 | 103.674 | 4 | 49 |
| 60 | racer-filtered rated | 12 | 12 | 16.093 | 68.562 | 4 | 29 |
| 60 | machine-filtered rated | 12 | 12 | 20.663 | 102.014 | 4 | 29 |
| 60 | version-filtered rated | 15 | 12 | 17.464 | 88.346 | 4 | 42 |
| 60 | title search, rated | 6 | 6 | 14.714 | 54.693 | 4 | 25 |
| 60 | rated, page 4 | 60 | 12 | 13.824 | 87.064 | 4 | 48 |
| 500 | newest, page 0 | 500 | 12 | 12.208 | 97.133 | 4 | 50 |
| 500 | score, page 0 | 500 | 12 | 19.506 | 86.300 | 4 | 44 |
| 500 | rated, page 0 | 500 | 12 | 18.715 | 81.500 | 4 | 44 |
| 500 | racer-filtered rated | 100 | 12 | 13.926 | 79.121 | 4 | 37 |
| 500 | machine-filtered rated | 100 | 12 | 15.816 | 72.310 | 4 | 37 |
| 500 | version-filtered rated | 125 | 12 | 16.095 | 81.134 | 4 | 44 |
| 500 | title search, rated | 50 | 12 | 16.880 | 69.922 | 4 | 37 |
| 500 | rated, page 4 | 500 | 12 | 16.495 | 76.881 | 4 | 44 |
| 5,000 | newest, page 0 | 5,000 | 12 | 18.766 | 90.469 | 4 | 50 |
| 5,000 | score, page 0 | 5,000 | 12 | 87.811 | 127.926 | 4 | 44 |
| 5,000 | rated, page 0 | 5,000 | 12 | 72.840 | 145.434 | 4 | 44 |
| 5,000 | racer-filtered rated | 1,000 | 12 | 26.885 | 80.027 | 4 | 37 |
| 5,000 | machine-filtered rated | 1,000 | 12 | 29.666 | 77.647 | 4 | 37 |
| 5,000 | version-filtered rated | 1,250 | 12 | 33.021 | 87.395 | 4 | 44 |
| 5,000 | title search, rated | 500 | 12 | 24.763 | 73.484 | 4 | 37 |
| 5,000 | rated, page 4 | 5,000 | 12 | 61.723 | 115.948 | 4 | 46 |

### Pass 1 to Pass 2 comparison at 5,000 builds

Positive percentages mean a lower median. Timing is noisy on this single development workstation;
the query-count change is deterministic and should carry more weight.

| Scenario | Service before | Service after | Service improvement | HTTP before | HTTP after | HTTP improvement | Service SQL before / after | HTTP SQL before / after |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| NEWEST | 508.365 | 18.766 | 96.3% | 649.014 | 90.469 | 86.1% | 315 / 4 | 361 / 50 |
| SCORE | 855.950 | 87.811 | 89.7% | 560.504 | 127.926 | 77.2% | 315 / 4 | 351 / 44 |
| BEST_RATED | 444.796 | 72.840 | 83.6% | 482.667 | 145.434 | 69.9% | 315 / 4 | 351 / 44 |

The former 313 candidate gadget-fetch statements disappeared in every 5,000-candidate service
scenario. Service SQL fell from 315 to 4 statements: one lightweight candidate projection, one vote
summary query, one page build query, and one page gadget-collection fetch. For NEWEST the vote query
is page-only; for SCORE and BEST_RATED it remains candidate-wide as required for exact ranking.
Statement count is now constant with candidate count at the measured page size.

The next dominant measured database concern is the deliberately unoptimized response-enrichment
fan-out: full 12-item HTTP responses used 44-50 statements for the unfiltered 5,000-build page-0
scenarios, of which 40-46 occurred after the four-statement service phase. SCORE and BEST_RATED also
retain O(N) candidate vote aggregation and application sorting; their 5,000-candidate service medians
remain materially higher than NEWEST even though SQL statement counts are equal. This pass does not
optimize either concern.

### NEWEST vote-summary impact

At baseline, production NEWEST executed the same all-candidate bulk vote query as SCORE and BEST_RATED even though
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

These were the proposals recorded with the baseline. Optimization Pass 1 above implements the first
two; the remaining candidates are unchanged and require separate evidence and review.

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
