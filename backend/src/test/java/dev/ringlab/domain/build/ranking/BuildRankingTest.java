package dev.ringlab.domain.build.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.vote.VoteSummary;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class BuildRankingTest {
  @Test
  void bestRatedPreservesSignConfidenceAndTieBreakPolicy() {
    Build tiny = build("00000000-0000-0000-0000-000000000005", 5);
    Build eight = build("00000000-0000-0000-0000-000000000004", 4);
    Build good = build("00000000-0000-0000-0000-000000000003", 3);
    Build perfect = build("00000000-0000-0000-0000-000000000002", 2);
    Build strong = build("00000000-0000-0000-0000-000000000001", 1);
    Build positive = build("00000000-0000-0000-0000-000000000006", 6);
    Build balanced = build("00000000-0000-0000-0000-000000000007", 7);
    Build empty = build("00000000-0000-0000-0000-000000000008", 8);
    Build negative = build("00000000-0000-0000-0000-000000000009", 9);
    Build oneDown = build("00000000-0000-0000-0000-000000000010", 10);
    Build twoDown = build("00000000-0000-0000-0000-000000000011", 11);
    Map<UUID, VoteSummary> facts = Map.ofEntries(
        Map.entry(strong.id(), new VoteSummary(40, 1)),
        Map.entry(perfect.id(), new VoteSummary(20, 0)),
        Map.entry(good.id(), new VoteSummary(30, 5)),
        Map.entry(eight.id(), new VoteSummary(8, 0)),
        Map.entry(tiny.id(), new VoteSummary(3, 0)),
        Map.entry(positive.id(), new VoteSummary(1, 0)),
        Map.entry(balanced.id(), new VoteSummary(20, 20)),
        Map.entry(negative.id(), new VoteSummary(20, 40)),
        Map.entry(oneDown.id(), new VoteSummary(0, 1)),
        Map.entry(twoDown.id(), new VoteSummary(0, 2)));

    List<BuildRanking.Candidate> actual = candidates(twoDown, empty, tiny, negative, strong,
        balanced, perfect, positive, good, oneDown, eight);
    actual.sort(BuildRanking.comparator(BuildSort.BEST_RATED, facts));

    assertEquals(List.of(strong, perfect, good, eight, tiny, positive,
        balanced, empty, negative, oneDown, twoDown).stream().map(Build::id).toList(), ids(actual));
  }

  @Test
  void allSortsUseCreationTimeThenUuidAsFinalTieBreaks() {
    Build old = build("00000000-0000-0000-0000-000000000003", 1);
    Build laterSecond = build("00000000-0000-0000-0000-000000000002", 2);
    Build laterFirst = build("00000000-0000-0000-0000-000000000001", 2);
    Map<UUID, VoteSummary> equal = Map.of(
        old.id(), new VoteSummary(3, 0), laterSecond.id(), new VoteSummary(3, 0),
        laterFirst.id(), new VoteSummary(3, 0));
    for (BuildSort sort : BuildSort.values()) {
      List<BuildRanking.Candidate> actual = candidates(old, laterSecond, laterFirst);
      actual.sort(BuildRanking.comparator(sort, equal));
      assertEquals(List.of(laterFirst.id(), laterSecond.id(), old.id()), ids(actual));
    }
  }

  private Build build(String id, int age) {
    UUID value = UUID.fromString(id);
    Instant time = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(age);
    return new Build(value, id, "", value, value, value, value, value,
        null, null, List.of(), time, time);
  }

  private List<BuildRanking.Candidate> candidates(Build... builds) {
    return Arrays.stream(builds).map(build ->
        new BuildRanking.Candidate(build.id(), build.createdAt()))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  private List<UUID> ids(List<BuildRanking.Candidate> candidates) {
    return candidates.stream().map(BuildRanking.Candidate::id).toList();
  }
}
