package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.domain.auth.ExternalIdentity;
import dev.ringlab.port.out.ExternalIdentityRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
@RequiredArgsConstructor
public class ExternalIdentityDbAdapter implements ExternalIdentityRepository {
  private final EntityManager em;

  public Optional<ExternalIdentity> find(String provider, String subject) {
    var key = new ExternalIdentityDbEntity.Key();
    key.provider = provider;
    key.subject = subject;
    return Optional.ofNullable(em.find(ExternalIdentityDbEntity.class, key))
        .map(e -> new ExternalIdentity(e.userId, e.provider, e.subject, e.createdAt));
  }

  public void create(ExternalIdentity identity) {
    var entity = new ExternalIdentityDbEntity();
    entity.userId = identity.userId();
    entity.provider = identity.provider();
    entity.subject = identity.subject();
    entity.createdAt = identity.createdAt();
    try {
      em.persist(entity);
      em.flush();
    } catch (ConstraintViolationException e) {
      if ("23505".equals(e.getSQLState())
          && "external_identities_pkey".equals(e.getConstraintName()))
        throw new AlreadyExistsException("Sign-in was completed concurrently. Please try again.");
      throw e;
    }
  }
}
