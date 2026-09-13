package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.domain.auth.EmailVerificationToken;
import dev.ringlab.port.out.EmailVerificationTokenRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class EmailVerificationTokenDbAdapter
    implements EmailVerificationTokenRepository, PanacheRepositoryBase<EmailVerificationTokenDbEntity, UUID> {
  @Override
  public void replace(EmailVerificationToken token) {
    delete("userId", token.userId());
    var entity = new EmailVerificationTokenDbEntity();
    entity.userId = token.userId();
    entity.tokenHash = token.tokenHash();
    entity.expiresAt = token.expiresAt();
    entity.createdAt = token.createdAt();
    persist(entity);
  }

  @Override
  public Optional<EmailVerificationToken> byTokenHashForUpdate(String tokenHash) {
    return find("tokenHash", tokenHash).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional()
        .map(entity -> new EmailVerificationToken(entity.userId, entity.tokenHash, entity.expiresAt, entity.createdAt));
  }

  @Override
  public void delete(UUID userId) {
    delete("userId", userId);
  }
}
