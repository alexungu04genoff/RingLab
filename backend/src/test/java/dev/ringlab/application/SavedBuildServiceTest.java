package dev.ringlab.application;

import dev.ringlab.application.build.SavedBuildService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildRanking;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.SavedBuildRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedBuildServiceTest {
  private final UUID actor = UUID.randomUUID();
  private final List<Build> available = new ArrayList<>();
  private int hydrationCalls;
  private final BuildRepository builds = new BuildRepository() {
    public Optional<Build> find(UUID id) { return available.stream().filter(b -> b.id().equals(id)).findFirst(); }
    public List<Build> findAll(Collection<UUID> ids) { hydrationCalls++; return available.stream().filter(b -> ids.contains(b.id())).toList(); }
    public List<BuildRanking.Candidate> searchCandidates(Filter filter) { throw new AssertionError("No public ranking for bookmarks"); }
    public void save(Build build) { throw new AssertionError("Must not modify the source"); }
    public void delete(UUID id) { throw new AssertionError("Must not delete the source"); }
  };
  private final Map<UUID, Instant> bookmarks = new LinkedHashMap<>();
  private final SavedBuildRepository repository = new SavedBuildRepository() {
    public Instant save(UUID user, UUID id) { assertEquals(actor, user); return bookmarks.computeIfAbsent(id, ignored -> Instant.now()); }
    public void remove(UUID user, UUID id) { assertEquals(actor, user); bookmarks.remove(id); }
    public Set<UUID> status(UUID user, Set<UUID> ids) { assertEquals(actor,user); var result = new HashSet<>(bookmarks.keySet()); result.retainAll(ids); return result; }
    public Page list(UUID user, String search, UUID version, int page, int size) {
      assertEquals(actor,user); assertEquals("query",search); assertEquals(2,page); assertEquals(12,size);
      return new Page(bookmarks.entrySet().stream().map(e -> new Bookmark(e.getKey(),e.getValue())).toList(), 30);
    }
  };
  private final SavedBuildService service = new SavedBuildService(repository, builds);
  private Build build() {
    var id = UUID.randomUUID();
    var build = new Build(id,"Live title","",UUID.randomUUID(),id,id,id,id,null,null,List.of(),Instant.EPOCH,Instant.EPOCH);
    available.add(build); return build;
  }
  @Test void savesExistingBuildsAndRemovesOnlyBookmarks() {
    var build = build();
    assertThrows(NotFoundException.class, () -> service.save(actor,UUID.randomUUID()));
    assertTrue(bookmarks.isEmpty());
    var time = service.save(actor,build.id());
    assertEquals(time,service.save(actor,build.id()));
    assertEquals(Set.of(build.id()),service.status(actor,Set.of(build.id(),UUID.randomUUID())));
    service.remove(actor,build.id()); service.remove(actor,build.id());
    assertTrue(bookmarks.isEmpty()); assertEquals(List.of(build),available);
  }
  @Test void hydratesOneBoundedPageAndRestoresSavedOrderIncludingDeletionRaces() {
    var first = build(); var second = build();
    bookmarks.put(second.id(),Instant.EPOCH.plusSeconds(2));
    bookmarks.put(UUID.randomUUID(),Instant.EPOCH.plusSeconds(1));
    bookmarks.put(first.id(),Instant.EPOCH);
    var page = service.list(actor,"query",null,2,12);
    assertEquals(List.of(second,first),page.items().stream().map(SavedBuildService.Item::build).toList());
    assertEquals(Instant.EPOCH.plusSeconds(2),page.items().getFirst().savedAt());
    assertEquals(30,page.total()); assertEquals(1,hydrationCalls);
  }
}
