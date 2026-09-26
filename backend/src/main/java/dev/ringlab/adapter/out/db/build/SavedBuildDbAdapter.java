package dev.ringlab.adapter.out.db.build;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.SavedBuildRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class SavedBuildDbAdapter implements SavedBuildRepository {
  private final EntityManager em;

  public Instant save(UUID userId, UUID buildId) {
    try {
      em.createNativeQuery("INSERT INTO saved_builds (user_id, build_id) VALUES (:user, :build) ON CONFLICT (user_id, build_id) DO NOTHING")
          .setParameter("user", userId).setParameter("build", buildId).executeUpdate();
    } catch (ConstraintViolationException exception) {
      if ("23503".equals(exception.getSQLState()) && "saved_builds_build_id_fkey".equals(exception.getConstraintName())) {
        throw NotFoundException.missing("Build");
      }
      throw exception;
    }
    return em.createQuery("select s.savedAt from SavedBuildDbEntity s where s.userId = :user and s.buildId = :build", Instant.class)
        .setParameter("user", userId).setParameter("build", buildId).getSingleResult();
  }

  public void remove(UUID userId, UUID buildId) {
    em.createQuery("delete from SavedBuildDbEntity s where s.userId = :user and s.buildId = :build")
        .setParameter("user", userId).setParameter("build", buildId).executeUpdate();
  }

  public Set<UUID> status(UUID userId, Set<UUID> ids) {
    if (ids.isEmpty()) return Set.of();
    return Set.copyOf(em.createQuery("select s.buildId from SavedBuildDbEntity s where s.userId = :user and s.buildId in :ids", UUID.class)
        .setParameter("user", userId).setParameter("ids", ids).getResultList());
  }

  public Page list(UUID userId, String search, UUID versionId, int page, int size) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(SavedBuildDbEntity.class);
    var saved = query.from(SavedBuildDbEntity.class);
    query.select(saved).where(filters(cb, query, saved, userId, search, versionId));
    query.orderBy(cb.desc(saved.get("savedAt")), cb.asc(saved.get("buildId")));
    var items = em.createQuery(query).setFirstResult(page * size).setMaxResults(size).getResultList()
        .stream().map(s -> new Bookmark(s.buildId, s.savedAt)).toList();
    var count = cb.createQuery(Long.class);
    var countSaved = count.from(SavedBuildDbEntity.class);
    count.select(cb.count(countSaved)).where(filters(cb, count, countSaved, userId, search, versionId));
    return new Page(items, em.createQuery(count).getSingleResult());
  }

  private Predicate[] filters(CriteriaBuilder cb, CriteriaQuery<?> query, Root<SavedBuildDbEntity> saved,
      UUID userId, String search, UUID versionId) {
    var build = query.from(BuildDbEntity.class);
    var predicates = BuildDbAdapter.filters(cb, query, build,
        new BuildRepository.Filter(search, null, null, null, versionId));
    predicates.add(cb.equal(saved.get("userId"), userId));
    predicates.add(cb.equal(saved.get("buildId"), build.get("id")));
    return predicates.toArray(Predicate[]::new);
  }
}
