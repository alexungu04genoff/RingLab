package dev.ringlab.adapter.in.rest.ratelimit;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

enum RequestRateLimitPolicy {
  GENERAL_READ(IdentityScope.CLIENT_IP),
  BUILD_LIST(IdentityScope.CLIENT_IP),
  NEWS(IdentityScope.CLIENT_IP),
  LOGIN(IdentityScope.CLIENT_IP),
  GOOGLE_LOGIN(IdentityScope.CLIENT_IP),
  REGISTRATION(IdentityScope.CLIENT_IP),
  BUILD_CREATION(IdentityScope.AUTHENTICATED_USER),
  COMMENT_CREATION(IdentityScope.AUTHENTICATED_USER),
  VOTING(IdentityScope.AUTHENTICATED_USER),
  AUTHENTICATED_MUTATION(IdentityScope.AUTHENTICATED_USER);

  private static final Pattern COMMENT_PATH =
      Pattern.compile("^/api/builds/[^/]+/comments/?$");
  private static final Pattern VOTE_PATH = Pattern.compile("^/api/builds/[^/]+/vote/?$");

  private final IdentityScope identityScope;

  RequestRateLimitPolicy(IdentityScope identityScope) {
    this.identityScope = identityScope;
  }

  IdentityScope identityScope() {
    return identityScope;
  }

  static Optional<RequestRateLimitPolicy> forRequest(String requestMethod, String requestPath) {
    String method = requestMethod.toUpperCase(Locale.ROOT);
    String path = requestPath.startsWith("/") ? requestPath : "/" + requestPath;
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    if (!path.startsWith("/api/") || method.equals("OPTIONS")) {
      return Optional.empty();
    }
    if (method.equals("POST") && path.equals("/api/auth/login")) {
      return Optional.of(LOGIN);
    }
    if (method.equals("POST") && path.equals("/api/auth/google")) {
      return Optional.of(GOOGLE_LOGIN);
    }
    if (method.equals("POST") && path.equals("/api/auth/register")) {
      return Optional.of(REGISTRATION);
    }
    if (method.equals("GET") && path.equals("/api/builds")) {
      return Optional.of(BUILD_LIST);
    }
    if (method.equals("GET") && path.equals("/api/news")) {
      return Optional.of(NEWS);
    }
    if (method.equals("POST") && path.equals("/api/builds")) {
      return Optional.of(BUILD_CREATION);
    }
    if (method.equals("POST") && COMMENT_PATH.matcher(path).matches()) {
      return Optional.of(COMMENT_CREATION);
    }
    if ((method.equals("PUT") || method.equals("DELETE"))
        && VOTE_PATH.matcher(path).matches()) {
      return Optional.of(VOTING);
    }
    if (method.equals("GET") || method.equals("HEAD")) {
      return Optional.of(GENERAL_READ);
    }
    if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")
        || method.equals("DELETE")) {
      return Optional.of(AUTHENTICATED_MUTATION);
    }
    return Optional.empty();
  }

  enum IdentityScope {
    CLIENT_IP,
    AUTHENTICATED_USER
  }
}
