package dev.ringlab.adapter.out.steam;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "steam-news")
@Path("/ISteamNews/GetNewsForApp/v2/")
@Produces(MediaType.APPLICATION_JSON)
public interface SteamNewsClient {
  @GET
  SteamNewsResponse latest(@QueryParam("appid") int appId,
      @QueryParam("count") int count, @QueryParam("maxlength") int maxLength);
}
