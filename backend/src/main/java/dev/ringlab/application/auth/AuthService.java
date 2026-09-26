package dev.ringlab.application.auth;

import dev.ringlab.domain.auth.User;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.application.validation.ProfanityPolicy;
import dev.ringlab.port.out.UserRepository;
import dev.ringlab.application.auth.EmailVerificationService.VerificationEmail;
import dev.ringlab.application.ForbiddenException;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository users;
  private final EmailVerificationService verification;
  private final Validator validator;
  private final ProfanityPolicy profanity;

  private record RegistrationInput(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}") String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 8, max = 72) String password) {}

  private record LoginInput(
      @NotBlank @Size(max = 30) String username,
      @NotNull @Size(min = 1, max = 72) String password) {}

  private void validateInput(Object input) {
    validator.validate(input).stream()
        .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
        .findFirst().ifPresent(violation -> {
          throw new ValidationException(violation.getPropertyPath() + ": " + violation.getMessage());
        });
  }
  private final String dummyHash = BcryptUtil.bcryptHash("ringlab-dummy-password");

  @Transactional
  public VerificationEmail register(String username, String email, String password) {
    validateInput(new RegistrationInput(username, email, password));
    username = username.toLowerCase(Locale.ROOT);
    email = email.toLowerCase(Locale.ROOT);
    profanity.requireClean(username);
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ValidationException("Password must be at most 72 UTF-8 bytes");
    if (users.exists(username, email))
      throw new AlreadyExistsException("Username or email already registered");
    Instant now = Instant.now();
    User user = new User(UUID.randomUUID(), username, email, BcryptUtil.bcryptHash(password), null, now);
    users.create(user);
    return verification.issueVerification(user, now);
  }

  public User login(String username, String password) {
    validateInput(new LoginInput(username, password));
    var user = users.byUsername(username.toLowerCase(Locale.ROOT));
    boolean valid = BcryptUtil.matches(password, user.map(User::passwordHash).orElse(dummyHash));
    if (user.isEmpty() || user.get().passwordHash() == null || !valid)
      throw new AuthenticationException("Invalid username or password");
    if (user.get().emailVerifiedAt() == null)
      throw new ForbiddenException("Please verify your email before signing in.");
    return user.get();
  }

  public User current(UUID id) {
    return users.byId(id).orElseThrow(() -> NotFoundException.missing("User"));
  }
}
