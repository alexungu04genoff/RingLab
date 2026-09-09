package dev.ringlab.adapter.in.rest.auth;

import jakarta.enterprise.context.RequestScoped;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public class CurrentUser {
  private final JsonWebToken jwt;

  public CurrentUser(JsonWebToken jwt) {
    this.jwt = jwt;
  }

  public UUID id() {
    return UUID.fromString(jwt.getSubject());
  }
}
