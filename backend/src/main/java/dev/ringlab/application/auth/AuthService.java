package dev.ringlab.application.auth;

import lombok.RequiredArgsConstructor;

import dev.ringlab.domain.auth.User;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.port.out.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository users;
  private final Validator validator;

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
  public User register(String username, String email, String password) {
    validateInput(new RegistrationInput(username, email, password));
    username = username.toLowerCase(Locale.ROOT);
    email = email.toLowerCase(Locale.ROOT);
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ValidationException("Password must be at most 72 UTF-8 bytes");
    if (users.exists(username, email))
      throw new AlreadyExistsException("Username or email already registered");
    User user =
        new User(
            UUID.randomUUID(), username, email, BcryptUtil.bcryptHash(password), Instant.now());
    users.create(user);
    return user;
  }

  public User login(String username, String password) {
    validateInput(new LoginInput(username, password));
    var user = users.byUsername(username.toLowerCase(Locale.ROOT));
    boolean valid = BcryptUtil.matches(password, user.map(User::passwordHash).orElse(dummyHash));
    if (user.isEmpty() || user.get().passwordHash() == null || !valid)
      throw new AuthenticationException("Invalid username or password");
    return user.get();
  }

  public User current(UUID id) {
    return users.byId(id).orElseThrow(() -> NotFoundException.missing("User"));
  }
}
