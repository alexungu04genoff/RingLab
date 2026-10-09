package dev.ringlab.application.build;

import dev.ringlab.port.in.BuildUseCase;

import lombok.RequiredArgsConstructor;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.BuildVisibility;
import dev.ringlab.domain.build.ranking.BuildRanking;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.domain.gamedata.GameVersion;
import dev.ringlab.domain.vote.VoteSummary;
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
public class BuildService implements BuildUseCase {

  private final BuildRepository builds;
  private final GameDataRepository game;
  private final VoteRepository votes;
  private final BuildDraftValidator drafts;
  private final jakarta.enterprise.event.Event<BuildPublicationChanged> publicationChanges;

  public Build get(UUID id) {
    return get(id, null);
  }

  public Build get(UUID id, UUID viewer) {
    if (id == null) throw new ValidationException("Missing build ID");
    return BuildAccessPolicy.requireReadable(
        builds.find(id).orElseThrow(() -> NotFoundException.missing("Build")), viewer);
  }

  /** Community mutations and publication edits serialize on the same parent row. */
  public Build lockPublic(UUID id) {
    return BuildAccessPolicy.requirePublic(locked(id));
  }

  private Build locked(UUID id) {
    if (id == null) throw new ValidationException("Missing build ID");
    return builds.findForUpdate(id).orElseThrow(() -> NotFoundException.missing("Build"));
  }

  public Optional<Build> remixSource(Build build) {
    return build.remixedFromBuildId() == null
        ? Optional.empty() : builds.find(build.remixedFromBuildId()).filter(BuildAccessPolicy::publiclyReadable);
  }

  public Page list(Query query) {
    return list(query, null, BuildVisibility.PUBLIC);
  }

  public Page listMine(UUID actor, Query query, BuildVisibility visibility) {
    if (actor == null) throw new dev.ringlab.application.AuthenticationException("Account unavailable");
    validateQuery(query);
    if (query.filter().authorId() != null)
      throw new ValidationException("My Builds cannot be combined with authorId");
    return list(query, actor, visibility);
  }

  private Page list(Query query, UUID owner, BuildVisibility visibility) {
    validateQuery(query);
    var filter = query.filter();
    List<BuildRanking.Candidate> candidates = builds.searchCandidates(new BuildRepository.Filter(
        filter.search(), filter.racerId(), filter.machineId(), filter.authorId(), filter.gameVersionId(),
        filter.excludedIds(), filter.mapId(), filter.includeAllMaps(), owner, visibility));
    var summaries = query.sort() == BuildSort.NEWEST
        ? Map.<UUID, VoteSummary>of()
        : votes.summaries(candidates.stream().map(BuildRanking.Candidate::id).toList());
    Map<UUID, LocalDate> releaseDates = releaseDates(query.sort());
    List<BuildRanking.Candidate> ranked = candidates.stream()
        .sorted(BuildRanking.comparator(query.sort(), summaries, releaseDates, owner == null))
        .toList();
    long offset = (long) query.page() * query.size();
    if (offset >= ranked.size()) return new Page(List.of(), ranked.size(), Map.of());
    int from = (int) offset;
    int to = (int) Math.min(offset + query.size(), ranked.size());
    List<UUID> pageIds = ranked.subList(from, to).stream().map(BuildRanking.Candidate::id).toList();
    List<Build> items = hydrateInRankedOrder(pageIds).stream()
        .filter(build -> BuildAccessPolicy.publiclyReadable(build) || build.authorId().equals(owner)).toList();
    return new Page(items, ranked.size(), pageSummaries(query.sort(), items, summaries));
  }

  private void validateQuery(Query query) {
    if (query == null || query.filter() == null || query.sort() == null)
      throw new ValidationException("Missing build query, filter or sort");
    if (query.page() < 0 || query.size() < 1 || query.size() > 50)
      throw new ValidationException("Page must be nonnegative and size must be between 1 and 50");
    if (query.filter().search() != null && query.filter().search().length() > 120)
      throw new ValidationException("Search must be at most 120 characters");
    drafts.validateMapFilter(query.filter().mapId());
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
    if (draft.remixedFromBuildId() != null)
      BuildAccessPolicy.requireReadable(locked(draft.remixedFromBuildId()), author);
    var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var visibility = draft.visibility() == null ? BuildVisibility.PUBLIC : draft.visibility();
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
            draft.recommendedMapIds() == null ? java.util.Set.of() : java.util.Set.copyOf(draft.recommendedMapIds()),
            now,
            now,
            visibility,
            visibility == BuildVisibility.PUBLIC ? now : null);
    builds.save(build);
    return build;
  }

  @Transactional
  public Build edit(UUID id, UUID actor, Draft draft) {
    var existing = BuildAccessPolicy.requireOwner(locked(id), actor);
    if (draft.expectedUpdatedAt() != null && !draft.expectedUpdatedAt().equals(existing.updatedAt()))
      throw new ValidationException("This build changed. Reload it before saving.", "expectedUpdatedAt");
    drafts.validate(draft);
    var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (!now.isAfter(existing.updatedAt())) now = existing.updatedAt().plusNanos(1000);
    var visibility = draft.visibility() == null ? existing.visibility() : draft.visibility();
    var firstPublishedAt = existing.firstPublishedAt() == null && visibility == BuildVisibility.PUBLIC
        ? now : existing.firstPublishedAt();
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
            draft.recommendedMapIds() == null ? existing.recommendedMapIds() : java.util.Set.copyOf(draft.recommendedMapIds()),
            existing.createdAt(),
            now,
            visibility,
            firstPublishedAt);
    builds.save(build);
    if (visibility != existing.visibility()) publicationChanges.fire(new BuildPublicationChanged());
    return build;
  }

  @Transactional
  public void delete(UUID id, UUID actor) {
    BuildAccessPolicy.requireOwner(locked(id), actor);
    builds.delete(id);
    publicationChanges.fire(new BuildPublicationChanged());
  }
}
