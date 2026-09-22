package dev.ringlab.application.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildRanking;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.domain.gamedata.GameVersion;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import dev.ringlab.port.out.VoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class BuildService {
  public record Query(BuildRepository.Filter filter, BuildSort sort, int page, int size) {}

  public record Page(List<Build> items, long total, Map<UUID, VoteSummary> summaries) {
    public Page {
      items = List.copyOf(items);
      summaries = Map.copyOf(summaries);
    }

    public Page(List<Build> items, long total) {
      this(items, total, Map.of());
    }

    public VoteSummary summary(UUID buildId) {
      return summaries.getOrDefault(buildId, new VoteSummary(0, 0));
    }
  }

  public record Draft(
      String title, String description, UUID racerId, UUID frontPartId,
      UUID rearPartId, UUID tirePartId, UUID gameVersionId, UUID remixedFromBuildId,
      List<UUID> gadgetIds) {}

  private final BuildRepository builds;
  private final GameDataRepository game;
  private final VoteRepository votes;
  private final BuildDraftValidator drafts;

  public Build get(UUID id) {
    if (id == null) throw new ValidationException("Missing build ID");
    return builds.find(id).orElseThrow(() -> NotFoundException.missing("Build"));
  }

  public Optional<Build> remixSource(Build build) {
    return build.remixedFromBuildId() == null
        ? Optional.empty() : builds.find(build.remixedFromBuildId());
  }

  public Page list(Query query) {
    validateQuery(query);
    List<BuildRanking.Candidate> candidates = builds.searchCandidates(query.filter());
    var summaries = query.sort() == BuildSort.NEWEST
        ? Map.<UUID, VoteSummary>of()
        : votes.summaries(candidates.stream().map(BuildRanking.Candidate::id).toList());
    Map<UUID, LocalDate> releaseDates = releaseDates(query.sort());
    List<BuildRanking.Candidate> ranked = candidates.stream()
        .sorted(BuildRanking.comparator(query.sort(), summaries, releaseDates))
        .toList();
    long offset = (long) query.page() * query.size();
    if (offset >= ranked.size()) return new Page(List.of(), ranked.size(), Map.of());
    int from = (int) offset;
    int to = (int) Math.min(offset + query.size(), ranked.size());
    List<UUID> pageIds = ranked.subList(from, to).stream().map(BuildRanking.Candidate::id).toList();
    List<Build> items = hydrateInRankedOrder(pageIds);
    return new Page(items, ranked.size(), pageSummaries(query.sort(), items, summaries));
  }

  private void validateQuery(Query query) {
    if (query == null || query.filter() == null || query.sort() == null)
      throw new ValidationException("Missing build query, filter or sort");
    if (query.page() < 0 || query.size() < 1 || query.size() > 50)
      throw new ValidationException("Page must be nonnegative and size must be between 1 and 50");
    if (query.filter().search() != null && query.filter().search().length() > 120)
      throw new ValidationException("Search must be at most 120 characters");
  }

  private Map<UUID, LocalDate> releaseDates(BuildSort sort) {
    return sort == BuildSort.BEST_RATED
        ? game.listGameVersions().stream().collect(java.util.stream.Collectors.toMap(
            GameVersion::id, GameVersion::releasedAt))
        : Map.of();
  }

  private List<Build> hydrateInRankedOrder(List<UUID> pageIds) {
    Map<UUID, Build> hydratedById = new HashMap<>();
    for (Build build : builds.findAll(pageIds)) hydratedById.put(build.id(), build);
    return pageIds.stream().map(hydratedById::get).filter(java.util.Objects::nonNull).toList();
  }

  private Map<UUID, VoteSummary> pageSummaries(
      BuildSort sort, List<Build> items, Map<UUID, VoteSummary> summaries) {
    return sort == BuildSort.NEWEST
        ? votes.summaries(items.stream().map(Build::id).toList())
        : items.stream().filter(build -> summaries.containsKey(build.id()))
            .collect(java.util.stream.Collectors.toMap(Build::id, build -> summaries.get(build.id())));
  }

  @Transactional
  public Build create(UUID author, Draft draft) {
    if (author == null) throw new ValidationException("Missing author ID");
    drafts.validate(draft);
    if (draft.remixedFromBuildId() != null && builds.find(draft.remixedFromBuildId()).isEmpty()) {
      throw new ValidationException("Unknown remix source build ID");
    }
    var now = Instant.now();
    var build =
        new Build(
            UUID.randomUUID(),
            draft.title().trim(),
            draft.description(),
            author,
            draft.racerId(),
            draft.frontPartId(),
            draft.rearPartId(),
            draft.tirePartId(),
            draft.gameVersionId(),
            draft.remixedFromBuildId(),
            draft.gadgetIds(),
            now,
            now);
    builds.save(build);
    return build;
  }

  @Transactional
  public Build edit(UUID id, UUID actor, Draft draft) {
    var existing = get(id);
    ForbiddenException.requireOwner(existing.authorId(), actor);
    drafts.validate(draft);
    var build =
        new Build(
            id,
            draft.title().trim(),
            draft.description(),
            existing.authorId(),
            draft.racerId(),
            draft.frontPartId(),
            draft.rearPartId(),
            draft.tirePartId(),
            draft.gameVersionId(),
            existing.remixedFromBuildId(),
            draft.gadgetIds(),
            existing.createdAt(),
            Instant.now());
    builds.save(build);
    return build;
  }

  @Transactional
  public void delete(UUID id, UUID actor) {
    ForbiddenException.requireOwner(get(id).authorId(), actor);
    builds.delete(id);
  }
}
