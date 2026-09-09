package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.application.gamedata.port.out.GameDataStore;
import dev.ringlab.domain.gamedata.GameItem;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class GameDataRestResource {
  public record ItemResponse(
      UUID id,
      String name,
      String racingType,
      String description,
      Integer slotCost,
      String imagePath) {
    static ItemResponse from(GameItem i) {
      return new ItemResponse(
          i.id(), i.name(), i.racingType(), i.description(), i.slotCost(), i.imagePath());
    }
  }

  private final GameDataStore store;

  public GameDataRestResource(GameDataStore store) {
    this.store = store;
  }

  private List<ItemResponse> list(GameDataStore.Kind kind) {
    return store.list(kind).stream().map(ItemResponse::from).toList();
  }

  @GET
  @Path("racers")
  public List<ItemResponse> racers() {
    return list(GameDataStore.Kind.RACER);
  }

  @GET
  @Path("machines")
  public List<ItemResponse> machines() {
    return list(GameDataStore.Kind.MACHINE);
  }

  @GET
  @Path("gadgets")
  public List<ItemResponse> gadgets() {
    return list(GameDataStore.Kind.GADGET);
  }
}
