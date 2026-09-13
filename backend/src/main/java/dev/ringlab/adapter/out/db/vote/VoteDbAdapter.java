package dev.ringlab.adapter.out.db.vote;

import dev.ringlab.domain.vote.Vote;
import dev.ringlab.application.NotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.VoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class VoteDbAdapter implements VoteRepository {
  private final EntityManager em;

  public VoteDbAdapter(EntityManager em) {
    this.em = em;
  }

  public void put(Vote v) {
    try {
      em.createNativeQuery(
              "insert into votes(id,user_id,build_id,value) values (:id,:user,:build,:value) on"
                  + " conflict(user_id,build_id) do update set value=excluded.value")
          .setParameter("id", UUID.randomUUID())
          .setParameter("user", v.userId())
          .setParameter("build", v.buildId())
          .setParameter("value", v.value())
          .executeUpdate();
    } catch (ConstraintViolationException exception) {
      if ("23503".equals(exception.getSQLState())
          && "votes_build_id_fkey".equals(exception.getConstraintName())) {
        throw NotFoundException.missing("Build");
      }
      throw exception;
    }
  }

  public void remove(UUID user, UUID build) {
    em.createQuery("delete VoteDbEntity where userId=:user and buildId=:build")
        .setParameter("user", user)
        .setParameter("build", build)
        .executeUpdate();
  }

  public VoteSummary summary(UUID build) {
    Object[] counts = em.createQuery(
            "select coalesce(sum(case when value = 1 then 1L else 0L end),0L),"
                + " coalesce(sum(case when value = -1 then 1L else 0L end),0L)"
                + " from VoteDbEntity where buildId=:build", Object[].class)
        .setParameter("build", build)
        .getSingleResult();
    return new VoteSummary((Long) counts[0], (Long) counts[1]);
  }

  public Map<UUID, VoteSummary> summaries(Collection<UUID> buildIds) {
    if (buildIds.isEmpty()) return Map.of();
    Map<UUID, VoteSummary> summaries = new HashMap<>();
    for (Object[] row : em.createQuery(
            "select buildId, sum(case when value = 1 then 1L else 0L end),"
                + " sum(case when value = -1 then 1L else 0L end)"
                + " from VoteDbEntity where buildId in :buildIds group by buildId",
            Object[].class)
        .setParameter("buildIds", buildIds)
        .getResultList()) {
      summaries.put((UUID) row[0], new VoteSummary((Long) row[1], (Long) row[2]));
    }
    return Map.copyOf(summaries);
  }

  public int value(UUID user, UUID build) {
    return em.createQuery(
            "select value from VoteDbEntity where userId=:user and buildId=:build", Short.class)
        .setParameter("user", user)
        .setParameter("build", build)
        .getResultStream()
        .findFirst()
        .orElse((short) 0);
  }
}
