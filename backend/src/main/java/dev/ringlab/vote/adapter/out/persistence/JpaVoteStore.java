package dev.ringlab.vote.adapter.out.persistence;

import dev.ringlab.vote.application.port.out.VoteStore;
import dev.ringlab.vote.domain.Vote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.UUID;

@ApplicationScoped
public class JpaVoteStore implements VoteStore {
  private final EntityManager em;

  public JpaVoteStore(EntityManager em) {
    this.em = em;
  }

  public void put(Vote v) {
    // PostgreSQL upsert preserves uniqueness even for simultaneous requests.
    em.createNativeQuery(
            "insert into votes(id,user_id,build_id,value) values (:id,:user,:build,:value) on"
                + " conflict(user_id,build_id) do update set value=excluded.value")
        .setParameter("id", UUID.randomUUID())
        .setParameter("user", v.userId())
        .setParameter("build", v.buildId())
        .setParameter("value", v.value())
        .executeUpdate();
  }

  public void remove(UUID user, UUID build) {
    em.createQuery("delete VoteEntity where userId=:user and buildId=:build")
        .setParameter("user", user)
        .setParameter("build", build)
        .executeUpdate();
  }

  public long score(UUID build) {
    return em.createQuery(
            "select coalesce(sum(value),0) from VoteEntity where buildId=:build", Long.class)
        .setParameter("build", build)
        .getSingleResult();
  }

  public int value(UUID user, UUID build) {
    return em.createQuery(
            "select value from VoteEntity where userId=:user and buildId=:build", Short.class)
        .setParameter("user", user)
        .setParameter("build", build)
        .getResultStream()
        .findFirst()
        .orElse((short) 0);
  }
}
