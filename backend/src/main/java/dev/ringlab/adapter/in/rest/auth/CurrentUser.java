package dev.ringlab.adapter.in.rest.auth;

import dev.ringlab.application.AuthenticationException;
import dev.ringlab.port.out.UserRepository;
import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public class CurrentUser {
  private final JsonWebToken jwt;
  private final UserRepository users;

  public CurrentUser(JsonWebToken jwt, UserRepository users) {
    this.jwt = jwt;
    this.users = users;
  }

  public UUID id() {
    UUID id;
    try {
      id = UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new AuthenticationException("Account unavailable");
    }
    if (users.byId(id).isEmpty()) throw new AuthenticationException("Account unavailable");
    return id;
  }
}
