package dev.ringlab.adapter.out.db.vote;

import dev.ringlab.application.vote.port.out.VoteStore;
import dev.ringlab.domain.vote.Vote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.UUID;

@ApplicationScoped
public class VoteDbAdapter implements VoteStore {
  private final EntityManager em;

  public VoteDbAdapter(EntityManager em) {
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
    em.createQuery("delete VoteDbEntity where userId=:user and buildId=:build")
        .setParameter("user", user)
        .setParameter("build", build)
        .executeUpdate();
  }

  public long score(UUID build) {
    return em.createQuery(
            "select coalesce(sum(value),0) from VoteDbEntity where buildId=:build", Long.class)
        .setParameter("build", build)
        .getSingleResult();
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
