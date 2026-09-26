package dev.ringlab.application.auth;

import dev.ringlab.application.ValidationException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  private PasswordPolicy() {}

  public static void validate(String password) {
    if (password == null || password.length() < 8 || password.length() > 72)
      throw new ValidationException("Password must be between 8 and 72 characters");
    if (password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ValidationException("Password must be at most 72 UTF-8 bytes");
  }
}
