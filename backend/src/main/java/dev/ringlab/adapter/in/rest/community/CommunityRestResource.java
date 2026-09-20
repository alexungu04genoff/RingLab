package dev.ringlab.adapter.in.rest.community;

import dev.ringlab.application.ValidationException;
import dev.ringlab.application.community.CommunitySnapshotCache;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/api/community/top-builds")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class CommunityRestResource {
  private final CommunitySnapshotCache snapshots;
  private final CommunityPublicUrls urls;

  @GET
  @Operation(summary = "Overall Best Rated: up to three eligible community builds across all patches; no query parameters")
  public Response top(@Context Request request, @Context UriInfo uri) {
    return response(request, uri, false);
  }

  @GET
  @Path("discord")
  @Operation(summary = "Format the shared top-three snapshot as a Discord message; never posts it")
  public Response discord(@Context Request request, @Context UriInfo uri) {
    return response(request, uri, true);
  }

  private Response response(Request request, UriInfo uri, boolean discord) {
    if (!uri.getQueryParameters().isEmpty()) throw new ValidationException("This endpoint accepts no query parameters");
    var snapshot = snapshots.get();
    var tag = new EntityTag(snapshot.revision() + (discord ? "-discord-v1" : "-json-v1"));
    var builder = request.evaluatePreconditions(tag);
    if (builder == null) {
      var body = TopBuildsResponse.from(snapshot, urls);
      builder = Response.ok(discord ? DiscordTopBuildsFormatter.format(body) : body);
    }
    // Revalidate on each request so browser/CDN TTL never adds to the server snapshot lifetime.
    return builder.tag(tag).header("Cache-Control", "public, no-cache, must-revalidate").build();
  }
}
