package dev.ringlab.application.auth;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.validation.ProfanityPolicy;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Creates passwordless accounts inside the sign-in service's transaction. */
@ApplicationScoped
@RequiredArgsConstructor
public class ExternalAccountRegistration {
  private final UserRepository users;
  private final ProfanityPolicy profanity;

  public User create(String normalizedEmail) {
    String username = availableUsername(normalizedEmail);
    Instant now = Instant.now();
    var user = new User(UUID.randomUUID(), username, normalizedEmail, null, now, now);
    users.create(user);
    return user;
  }

  private String availableUsername(String email) {
    String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9_]", "");
    if (base.length() < 3) base = "user_" + base;
    base = base.substring(0, Math.min(base.length(), 21));
    if (profanity.containsProfanity(base)) base = "user";

    String username = base;
    for (int attempt = 0; users.byUsername(username).isPresent(); attempt++) {
      if (attempt >= 10)
        throw new AlreadyExistsException("Could not reserve a username. Please try again.");
      username = base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
    profanity.requireClean(username);
    return username;
  }
}
