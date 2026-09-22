package dev.ringlab.application.auth;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.validation.ProfanityPolicy;
import dev.ringlab.domain.auth.ExternalIdentity;
import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.auth.VerifiedExternalIdentity;
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
  private final ProfanityPolicy profanity;

  @Transactional
  public User login(String credential) {
    var verified = verify(credential);
    var existing = identities.find(verified.provider(), verified.subject());
    if (existing.isPresent())
      return users.byId(existing.get().userId())
          .orElseThrow(() -> new AuthenticationException("Account unavailable"));

    return createAccount(verified);
  }

  private User createAccount(VerifiedExternalIdentity verified) {
    String email = verified.email().toLowerCase(Locale.ROOT);
    if (users.exists("", email))
      throw new AlreadyExistsException(
          "An account already exists with this email. Sign in with your username and password, "
              + "then link Google from your account page.");

    String username = availableUsername(email);
    Instant now = Instant.now();
    var user = new User(UUID.randomUUID(), username, email, null, now, now);
    users.create(user);
    identities.create(new ExternalIdentity(user.id(), verified.provider(), verified.subject(), now));
    return user;
  }

  private String availableUsername(String email) {
    String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9_]", "");
    if (base.length() < 3) base = "user_" + base;
    base = base.substring(0, Math.min(base.length(), 21));
    if (profanity.containsProfanity(base)) base = "user";
    String username = base;
    for (int attempt = 0; users.byUsername(username).isPresent(); attempt++) {
      if (attempt >= 10) throw new AlreadyExistsException("Could not reserve a username. Please try again.");
      username = base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    profanity.requireClean(username);
    return username;
  }

  @Transactional
  public void link(UUID userId, String credential) {
    var user = users.byId(userId)
        .orElseThrow(() -> new AuthenticationException("Account unavailable"));
    var verified = verify(credential);
    if (!user.email().equalsIgnoreCase(verified.email()))
      throw new ForbiddenException("Google account email must match your RingLab email.");

    var existing = identities.find(verified.provider(), verified.subject());
    if (existing.isPresent()) {
      if (existing.get().userId().equals(userId)) return;
      throw new AlreadyExistsException(
          "This Google account is already linked to another RingLab account.");
    }
    identities.create(
        new ExternalIdentity(userId, verified.provider(), verified.subject(), Instant.now()));
  }

  private VerifiedExternalIdentity verify(String credential) {
    if (credential == null || credential.isBlank() || credential.length() > 16384)
      throw new AuthenticationException("Invalid sign-in credential");
    return verifier.verify(credential);
  }
}
