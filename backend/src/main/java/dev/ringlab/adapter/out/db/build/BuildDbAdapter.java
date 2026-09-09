package dev.ringlab.adapter.out.db.build;

import dev.ringlab.application.build.port.out.BuildStore;
import dev.ringlab.domain.build.Build;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.*;

@ApplicationScoped
public class BuildDbAdapter implements BuildStore {
  private final EntityManager em;
  private final BuildDbMapper mapper;

  public BuildDbAdapter(EntityManager em, BuildDbMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  public Optional<Build> find(UUID id) {
    return Optional.ofNullable(mapper.toDomain(em.find(BuildDbEntity.class, id)));
  }

  public Page list(Filter f) {
    String where = " where 1=1";
    Map<String, Object> params = new HashMap<>();
    if (f.search() != null && !f.search().isBlank()) {
      where += " and locate(:search,lower(b.title)) > 0";
      params.put("search", f.search().toLowerCase(Locale.ROOT));
    }
    if (f.racerId() != null) {
      where += " and b.racerId=:racer";
      params.put("racer", f.racerId());
    }
    if (f.machineId() != null) {
      where += " and b.machineId=:machine";
      params.put("machine", f.machineId());
    }
    if (f.authorId() != null) {
      where += " and b.authorId=:author";
      params.put("author", f.authorId());
    }
    String order =
        "score".equals(f.sort())
            ? "(select coalesce(sum(v.value),0) from VoteDbEntity v where v.buildId=b.id) desc, "
            : "";
    var query =
        em.createQuery(
            "select b from BuildDbEntity b" + where + " order by " + order + "b.createdAt desc,b.id",
            BuildDbEntity.class);
    var count = em.createQuery("select count(b) from BuildDbEntity b" + where, Long.class);
    params.forEach(
        (k, v) -> {
          query.setParameter(k, v);
          count.setParameter(k, v);
        });
    var items =
        query.setFirstResult(f.page() * f.size()).setMaxResults(f.size()).getResultList().stream()
            .map(mapper::toDomain)
            .toList();
    return new Page(items, count.getSingleResult());
  }

  public void save(Build build) {
    em.merge(mapper.toEntity(build));
  }

  public void delete(UUID id) {
    em.remove(em.find(BuildDbEntity.class, id));
  }
}
