package dev.ringlab.application.auth;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.domain.auth.ExternalIdentity;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.ExternalIdentityRepository;
import dev.ringlab.port.out.ExternalIdentityVerifier;
import dev.ringlab.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class ExternalAuthService {
  private final ExternalIdentityVerifier verifier;
  private final ExternalIdentityRepository identities;
  private final UserRepository users;

  @Transactional
  public User login(String credential) {
    if (credential == null || credential.isBlank() || credential.length() > 16384)
      throw new AuthenticationException("Invalid sign-in credential");
    var verified = verifier.verify(credential);
    var existing = identities.find(verified.provider(), verified.subject());
    if (existing.isPresent())
      return users.byId(existing.get().userId())
          .orElseThrow(() -> new AuthenticationException("Account unavailable"));

    String email = verified.email().toLowerCase(Locale.ROOT);
    if (users.exists("", email))
      throw new AlreadyExistsException(
          "An account already exists with this email. Sign in with your existing account first.");

    String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9_]", "");
    if (base.length() < 3) base = "user_" + base;
    base = base.substring(0, Math.min(base.length(), 21));
    String username = base;
    for (int attempt = 0; users.byUsername(username).isPresent(); attempt++) {
      if (attempt >= 10) throw new AlreadyExistsException("Could not reserve a username. Please try again.");
      username = base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    Instant now = Instant.now();
    var user = new User(UUID.randomUUID(), username, email, null, now);
    users.create(user);
    identities.create(new ExternalIdentity(user.id(), verified.provider(), verified.subject(), now));
    return user;
  }
}
