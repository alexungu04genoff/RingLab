package dev.ringlab.adapter.in.rest.ratelimit;

import dev.ringlab.adapter.in.rest.ErrorRestExceptionMapper.ErrorResponse;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class RateLimitFilter implements ContainerRequestFilter {
  private static final String CLOUDFLARE_CLIENT_IP = "CF-Connecting-IP";

  private final InMemoryRateLimiter limiter;
  private final RateLimitSettings settings;
  private final SecurityIdentity securityIdentity;
  private final JsonWebToken jwt;
  private final HttpServerRequest serverRequest;

  @Inject
  RateLimitFilter(
      InMemoryRateLimiter limiter,
      RateLimitSettings settings,
      SecurityIdentity securityIdentity,
      JsonWebToken jwt,
      HttpServerRequest serverRequest) {
    this.limiter = limiter;
    this.settings = settings;
    this.securityIdentity = securityIdentity;
    this.jwt = jwt;
    this.serverRequest = serverRequest;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    Optional<RequestRateLimitPolicy> selected = RequestRateLimitPolicy.forRequest(
        requestContext.getMethod(), requestContext.getUriInfo().getPath());
    if (selected.isEmpty()) {
      return;
    }

    RequestRateLimitPolicy policy = selected.get();
    String clientIp = ClientIpResolver.resolve(
        serverRequest.remoteAddress().hostAddress(),
        requestContext.getHeaderString(CLOUDFLARE_CLIENT_IP),
        settings.trustCloudflareClientIp());
    Optional<String> authenticatedUserId = securityIdentity.isAnonymous()
        ? Optional.empty()
        : Optional.ofNullable(jwt.getSubject());
    String identity = RateLimitIdentity.resolve(policy, authenticatedUserId, clientIp);
    InMemoryRateLimiter.Decision decision = limiter.tryConsume(policy, identity);
    if (!decision.allowed()) {
      requestContext.abortWith(rejectionResponse(decision));
    }
  }

  static Response rejectionResponse(InMemoryRateLimiter.Decision decision) {
    return Response.status(Response.Status.TOO_MANY_REQUESTS)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .header("Retry-After", decision.retryAfterSeconds())
        .entity(new ErrorResponse("Too many requests"))
        .build();
  }
}
