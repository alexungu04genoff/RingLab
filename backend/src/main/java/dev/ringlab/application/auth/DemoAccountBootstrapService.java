package dev.ringlab.application.auth;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.UserRepository;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Explicit development fixture support. This bean is absent from production builds. */
@ApplicationScoped
@UnlessBuildProfile("prod")
public class DemoAccountBootstrapService {
  public record Account(String key, String username, String email) {}

  private static final String USERNAME_PREFIX = "ringlab_demo_";
  private static final String EMAIL_SUFFIX = "@example.test";
  private final UserRepository users;

  public DemoAccountBootstrapService(UserRepository users) {
    this.users = users;
  }

  @Transactional
  public List<User> bootstrap(List<Account> accounts, String password) {
    if (accounts == null || accounts.isEmpty() || accounts.size() > 200) {
      throw new ValidationException("Demo fixture must contain between 1 and 200 accounts");
    }
    if (password == null || password.length() < 8 || password.length() > 72) {
      throw new ValidationException("Invalid demo fixture password");
    }
    var identities = new HashSet<String>();
    var result = new ArrayList<User>();
    String passwordHash = null;
    Instant now = Instant.now();
    for (Account account : accounts) {
      if (account == null || account.key() == null || account.key().isBlank()) {
        throw new ValidationException("Demo fixture account key is required");
      }
      String username = account.username() == null ? "" : account.username().toLowerCase(Locale.ROOT);
      String email = account.email() == null ? "" : account.email().toLowerCase(Locale.ROOT);
      if (!username.startsWith(USERNAME_PREFIX) || !email.startsWith(USERNAME_PREFIX)
          || !email.endsWith(EMAIL_SUFFIX) || !identities.add(username) || !identities.add(email)) {
        throw new ValidationException("Invalid or duplicate reserved demo identity: " + account.key());
      }
      var existingByUsername = users.byUsername(username);
      var existingByEmail = users.byEmail(email);
      if (existingByUsername.isPresent() || existingByEmail.isPresent()) {
        if (existingByUsername.isEmpty() || existingByEmail.isEmpty()
            || !existingByUsername.get().id().equals(existingByEmail.get().id())
            || existingByUsername.get().passwordHash() == null
            || !BcryptUtil.matches(password, existingByUsername.get().passwordHash())) {
          throw new ValidationException("Conflicting demo identity: " + account.key());
        }
        if (existingByUsername.get().emailVerifiedAt() == null) {
          throw new ValidationException("Existing demo identity is not verified: " + account.key());
        }
        result.add(existingByUsername.get());
        continue;
      }
      if (passwordHash == null) passwordHash = BcryptUtil.bcryptHash(password);
      var user = new User(UUID.randomUUID(), username, email, passwordHash, now, now);
      users.create(user);
      result.add(user);
    }
    return List.copyOf(result);
  }
}
