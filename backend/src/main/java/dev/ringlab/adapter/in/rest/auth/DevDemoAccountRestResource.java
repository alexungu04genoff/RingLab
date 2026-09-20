package dev.ringlab.adapter.in.rest.auth;

import dev.ringlab.adapter.in.rest.auth.response.SessionResponse;
import dev.ringlab.adapter.in.rest.auth.response.UserResponse;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.auth.DemoAccountBootstrapService;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.smallrye.jwt.build.Jwt;
import io.vertx.core.http.HttpServerRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.net.InetAddress;
import java.util.List;

/** Loopback-only development fixture entry point; it is not present in production builds. */
@Path("/api/dev-fixtures/demo-accounts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@UnlessBuildProfile("prod")
public class DevDemoAccountRestResource {
  public record AccountRequest(@NotBlank String key, @NotBlank String username, @NotBlank String email) {}
  public record BootstrapRequest(
      @NotEmpty @Size(max = 200) List<@Valid @NotNull AccountRequest> accounts,
      @NotBlank @Size(min = 8, max = 72) String password) {}
  public record AccountSession(String key, SessionResponse session) {}

  private final DemoAccountBootstrapService service;
  private final HttpServerRequest request;

  public DevDemoAccountRestResource(DemoAccountBootstrapService service, HttpServerRequest request) {
    this.service = service;
    this.request = request;
  }

  @POST
  public List<AccountSession> bootstrap(@Valid @NotNull BootstrapRequest body) {
    requireLoopback();
    var definitions = body.accounts().stream()
        .map(account -> new DemoAccountBootstrapService.Account(
            account.key(), account.username(), account.email()))
        .toList();
    var users = service.bootstrap(definitions, body.password());
    var result = new java.util.ArrayList<AccountSession>();
    for (int index = 0; index < users.size(); index++) {
      var user = users.get(index);
      var session = new SessionResponse(
          Jwt.subject(user.id().toString()).upn(user.username()).groups("user").sign(),
          UserResponse.from(user));
      result.add(new AccountSession(body.accounts().get(index).key(), session));
    }
    return List.copyOf(result);
  }

  private void requireLoopback() {
    try {
      if (!InetAddress.getByName(request.remoteAddress().hostAddress()).isLoopbackAddress()) {
        throw new ForbiddenException("Development fixtures are available only from loopback");
      }
    } catch (java.net.UnknownHostException exception) {
      throw new ForbiddenException("Development fixtures are available only from loopback");
    }
  }
}
