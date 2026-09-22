package dev.ringlab.domain.build.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.vote.VoteSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class BuildRankingTest {
  private static final UUID NEW_PATCH = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OLD_PATCH = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final Map<UUID, LocalDate> RELEASE_DATES = Map.of(
      NEW_PATCH, LocalDate.of(2026, 8, 1), OLD_PATCH, LocalDate.of(2026, 3, 18));

  @Test
  void equalWilsonUsesReleaseChronologyBeforeZeroEvidenceAndSubmissionTime() {
    Instant oldTime = Instant.parse("2026-01-01T00:00:00Z");
    Instant newTime = oldTime.plusSeconds(100);
    var newerPatchDown = candidate(1, newTime, NEW_PATCH);
    var olderPatchUnrated = candidate(2, oldTime, OLD_PATCH);
    var samePatchUnrated = candidate(3, oldTime, NEW_PATCH);
    var samePatchFiveDown = candidate(4, newTime, NEW_PATCH);
    var unspecifiedUnrated = candidate(5, newTime, null);
    var facts = Map.of(
        newerPatchDown.id(), new VoteSummary(0, 1),
        samePatchFiveDown.id(), new VoteSummary(0, 5));
    var actual = new ArrayList<>(List.of(unspecifiedUnrated, samePatchFiveDown,
        olderPatchUnrated, newerPatchDown, samePatchUnrated));

    actual.sort(BuildRanking.comparator(BuildSort.BEST_RATED, facts, RELEASE_DATES));

    assertEquals(List.of(samePatchUnrated.id(), newerPatchDown.id(),
        samePatchFiveDown.id(), olderPatchUnrated.id(), unspecifiedUnrated.id()), ids(actual));
  }

  @Test
  void wilsonAlwaysPrecedesPatchAndNegativeNetCanBeatUnrated() {
    var olderNegative = candidate(1, Instant.EPOCH, OLD_PATCH);
    var newerUnrated = candidate(2, Instant.EPOCH.plusSeconds(1), NEW_PATCH);
    var actual = new ArrayList<>(List.of(newerUnrated, olderNegative));

    actual.sort(BuildRanking.comparator(BuildSort.BEST_RATED,
        Map.of(olderNegative.id(), new VoteSummary(20, 40)), RELEASE_DATES));

    assertEquals(List.of(olderNegative.id(), newerUnrated.id()), ids(actual));
  }

  @Test
  void equalNonZeroWilsonUsesPatchBeforeCreationAndVersionTextIsIrrelevant() {
    var newerPatchOldBuild = candidate(1, Instant.EPOCH, NEW_PATCH);
    var olderPatchNewBuild = candidate(2, Instant.EPOCH.plusSeconds(1), OLD_PATCH);
    var actual = new ArrayList<>(List.of(olderPatchNewBuild, newerPatchOldBuild));
    var facts = Map.of(newerPatchOldBuild.id(), new VoteSummary(8, 2),
        olderPatchNewBuild.id(), new VoteSummary(8, 2));

    actual.sort(BuildRanking.comparator(BuildSort.BEST_RATED, facts, RELEASE_DATES));

    assertEquals(List.of(newerPatchOldBuild.id(), olderPatchNewBuild.id()), ids(actual));
  }

  @Test
  void higherFullPrecisionWilsonWinsRegardlessOfDate() {
    Build higherOld = build("00000000-0000-0000-0000-000000000001", 1);
    Build lowerNew = build("00000000-0000-0000-0000-000000000002", 2);
    Map<UUID, VoteSummary> facts = Map.of(
        higherOld.id(), new VoteSummary(40, 1),
        lowerNew.id(), new VoteSummary(20, 1));

    assertBestRatedOrder(facts, List.of(higherOld.id(), lowerNew.id()), lowerNew, higherOld);
  }

  @Test
  void equalNonZeroWilsonUsesNewestFirst() {
    Build old = build("00000000-0000-0000-0000-000000000001", 1);
    Build newest = build("00000000-0000-0000-0000-000000000002", 2);
    Map<UUID, VoteSummary> facts = Map.of(
        old.id(), new VoteSummary(8, 2), newest.id(), new VoteSummary(8, 2));

    assertBestRatedOrder(facts, List.of(newest.id(), old.id()), old, newest);
  }

  @Test
  void unratedWilsonZeroRanksAboveNegativeEvidence() {
    Build unrated = build("00000000-0000-0000-0000-000000000001", 1);
    Build downvoted = build("00000000-0000-0000-0000-000000000002", 2);
    Map<UUID, VoteSummary> facts = Map.of(downvoted.id(), new VoteSummary(0, 1));

    assertBestRatedOrder(facts, List.of(unrated.id(), downvoted.id()), downvoted, unrated);
  }

  @Test
  void lessNegativeWilsonZeroRanksFirst() {
    Build oneDown = build("00000000-0000-0000-0000-000000000001", 1);
    Build fiveDownNewer = build("00000000-0000-0000-0000-000000000002", 2);
    Map<UUID, VoteSummary> facts = Map.of(
        oneDown.id(), new VoteSummary(0, 1), fiveDownNewer.id(), new VoteSummary(0, 5));

    assertBestRatedOrder(facts, List.of(oneDown.id(), fiveDownNewer.id()), fiveDownNewer, oneDown);
  }

  @Test
  void unratedWilsonZeroUsesNewestFirst() {
    Build old = build("00000000-0000-0000-0000-000000000001", 1);
    Build newest = build("00000000-0000-0000-0000-000000000002", 2);

    assertBestRatedOrder(Map.of(), List.of(newest.id(), old.id()), old, newest);
  }

  @Test
  void equivalentVoteStateAndTimestampUsesUuidAsDeterministicFinalTieBreak() {
    Build second = build("00000000-0000-0000-0000-000000000002", 1);
    Build first = build("00000000-0000-0000-0000-000000000001", 1);
    Map<UUID, VoteSummary> facts = Map.of(
        first.id(), new VoteSummary(3, 1), second.id(), new VoteSummary(3, 1));

    assertBestRatedOrder(facts, List.of(first.id(), second.id()), second, first);
  }

  @Test
  void equalRoundedDisplayScoreStillUsesFullPrecisionWilson() {
    Build higherOld = build("00000000-0000-0000-0000-000000000001", 1);
    Build lowerNew = build("00000000-0000-0000-0000-000000000002", 2);
    VoteSummary higher = new VoteSummary(16, 0);
    VoteSummary lower = new VoteSummary(38, 3);
    assertEquals(
        Math.round(WilsonScore.lowerBound(higher.upvotes(), higher.downvotes()) * 100),
        Math.round(WilsonScore.lowerBound(lower.upvotes(), lower.downvotes()) * 100));

    assertBestRatedOrder(Map.of(higherOld.id(), higher, lowerNew.id(), lower),
        List.of(higherOld.id(), lowerNew.id()), lowerNew, higherOld);
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
      actual.sort(BuildRanking.comparator(sort, equal, Map.of()));
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

  private BuildRanking.Candidate candidate(int id, Instant createdAt, UUID patch) {
    return new BuildRanking.Candidate(
        UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", id)),
        createdAt, patch);
  }

  private void assertBestRatedOrder(
      Map<UUID, VoteSummary> facts, List<UUID> expected, Build... unordered) {
    List<BuildRanking.Candidate> actual = candidates(unordered);
    actual.sort(BuildRanking.comparator(BuildSort.BEST_RATED, facts, Map.of()));
    assertEquals(expected, ids(actual));
  }
}
