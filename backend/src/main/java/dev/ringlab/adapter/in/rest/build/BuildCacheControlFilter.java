package dev.ringlab.adapter.in.rest.build;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Persisted build responses can lose public access at any time, including their child resources. */
@Provider
public class BuildCacheControlFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    String path = request.getUriInfo().getPath().replaceFirst("^/", "");
    if (path.equals("api/builds") || path.startsWith("api/builds/")
        || path.equals("api/saved-builds") || path.startsWith("api/saved-builds/")
        || path.startsWith("api/stats/persisted/") || path.startsWith("api/comments/")) {
      response.getHeaders().putSingle("Cache-Control", "private, no-store");
      response.getHeaders().add("Vary", "Authorization");
    }
  }
}
