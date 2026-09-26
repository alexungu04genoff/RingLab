package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.domain.auth.PasswordResetToken;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.PasswordResetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class PasswordResetDbAdapter implements PasswordResetRepository {
  private final EntityManager em;
  private final UserDbMapper mapper;

  public Optional<User> lockUser(UUID userId) {
    var user = em.find(UserDbEntity.class, userId, LockModeType.PESSIMISTIC_WRITE);
    // A prior email lookup may already have loaded this entity before the lock was acquired.
    if (user != null) em.refresh(user, LockModeType.PESSIMISTIC_WRITE);
    return Optional.ofNullable(user).map(mapper::toDomain);
  }

  public Optional<PasswordResetToken> byHash(String hash) {
    // Scalar projection avoids a stale managed token after waiting for the account lock.
    return em.createQuery("select new dev.ringlab.domain.auth.PasswordResetToken(t.userId, t.tokenHash, t.createdAt, t.expiresAt) from PasswordResetTokenDbEntity t where t.tokenHash = :hash", PasswordResetToken.class)
        .setParameter("hash", hash).getResultStream().findFirst();
  }

  public Optional<PasswordResetToken> byUser(UUID userId) {
    return em.createQuery("select new dev.ringlab.domain.auth.PasswordResetToken(t.userId, t.tokenHash, t.createdAt, t.expiresAt) from PasswordResetTokenDbEntity t where t.userId = :id", PasswordResetToken.class)
        .setParameter("id", userId).getResultStream().findFirst();
  }

  public void replace(PasswordResetToken token) {
    var entity = em.find(PasswordResetTokenDbEntity.class, token.userId());
    boolean newToken = entity == null;
    if (entity == null) {
      entity = new PasswordResetTokenDbEntity();
      entity.userId = token.userId();
    }
    entity.tokenHash = token.tokenHash();
    entity.createdAt = token.createdAt();
    entity.expiresAt = token.expiresAt();
    if (newToken) em.persist(entity);
    em.flush();
  }

  public void changePasswordAndVersion(UUID userId, String passwordHash) {
    var user = em.find(UserDbEntity.class, userId);
    user.passwordHash = passwordHash;
    user.authVersion = Math.incrementExact(user.authVersion);
  }

  public void delete(UUID userId) {
    em.createQuery("delete PasswordResetTokenDbEntity where userId = :id").setParameter("id", userId).executeUpdate();
  }
}
