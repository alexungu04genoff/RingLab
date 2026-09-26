package dev.ringlab.application.build;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.domain.build.Build;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.SavedBuildRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class SavedBuildService {
  private final SavedBuildRepository saved;
  private final BuildRepository builds;
  public record Item(Build build, Instant savedAt) {}
  public record Page(List<Item> items, long total) {
    public Page { items = List.copyOf(items); }
  }

  @Transactional
  public Instant save(UUID actor, UUID buildId) {
    builds.find(buildId).orElseThrow(() -> NotFoundException.missing("Build"));
    return saved.save(actor, buildId);
  }

  @Transactional
  public void remove(UUID actor, UUID buildId) { saved.remove(actor, buildId); }

  public Set<UUID> status(UUID actor, Set<UUID> ids) { return saved.status(actor, ids); }

  @Transactional
  public Page list(UUID actor, String search, UUID version, int page, int size) {
    var bookmarks = saved.list(actor, search, version, page, size);
    var hydrated = builds.findAll(bookmarks.items().stream().map(SavedBuildRepository.Bookmark::buildId).toList())
        .stream().collect(Collectors.toMap(Build::id, Function.identity()));
    return new Page(bookmarks.items().stream().filter(item -> hydrated.containsKey(item.buildId()))
        .map(item -> new Item(hydrated.get(item.buildId()), item.savedAt())).toList(), bookmarks.total());
  }
}
