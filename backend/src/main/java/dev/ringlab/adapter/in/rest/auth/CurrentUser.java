package dev.ringlab.adapter.in.rest.auth;

import dev.ringlab.application.AuthenticationException;
import dev.ringlab.port.in.AuthUseCase;
import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public class CurrentUser {
  private final JsonWebToken jwt;
  private final AuthUseCase accounts;

  public CurrentUser(JsonWebToken jwt, AuthUseCase accounts) {
    this.jwt = jwt;
    this.accounts = accounts;
  }

  public UUID id() {
    UUID id;
    try {
      id = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new AuthenticationException("Account unavailable");
    }
    Object claim = jwt.getClaim("authVersion");
    // Pre-versioning JWTs are version 0, valid only until the first password reset.
    String version = claim == null ? "0" : claim.toString();
    accounts.validateSession(id, version);
    return id;
  }

  /** The JWT extension rejects invalid bearer tokens; anonymous public reads have no subject. */
  public UUID optionalId() {
    return jwt.getSubject() == null ? null : id();
  }
}
