package dev.ringlab;

import dev.ringlab.adapter.out.db.auth.UserDbEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.jwt.build.Jwt;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;

/** Explicit authenticated fixture for catalog tests; not a registration/verification test. */
final class VerifiedUserFixture {
  private VerifiedUserFixture() {}

  static String createToken(EntityManager em) {
    var user = new UserDbEntity();
    user.id = UUID.randomUUID();
    user.username = "fixture_" + user.id.toString().replace("-", "").substring(0, 16);
    user.email = user.username + "@example.test";
    user.createdAt = Instant.now();
    user.emailVerifiedAt = user.createdAt;
    QuarkusTransaction.requiringNew().run(() -> em.persist(user));
    return Jwt.subject(user.id.toString()).upn(user.username).groups("user").sign();
  }
}
