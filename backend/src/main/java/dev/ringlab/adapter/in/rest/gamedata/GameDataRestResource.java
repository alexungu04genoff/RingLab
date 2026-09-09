package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.application.gamedata.port.out.GameDataStore;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.Racer;
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
    public static ItemResponse from(Racer racer) {
      return new ItemResponse(
          racer.id(), racer.name(), racer.racingType(), null, null, racer.imagePath());
    }

    public static ItemResponse from(Machine machine) {
      return new ItemResponse(
          machine.id(), machine.name(), machine.racingType(), null, null, machine.imagePath());
    }

    public static ItemResponse from(Gadget gadget) {
      return new ItemResponse(
          gadget.id(), gadget.name(), null, gadget.description(), gadget.slotCost(), gadget.imagePath());
    }
  }

  private final GameDataStore store;

  public GameDataRestResource(GameDataStore store) {
    this.store = store;
  }

  @GET
  @Path("racers")
  public List<ItemResponse> racers() {
    return store.listRacers().stream().map(ItemResponse::from).toList();
  }

  @GET
  @Path("machines")
  public List<ItemResponse> machines() {
    return store.listMachines().stream().map(ItemResponse::from).toList();
  }

  @GET
  @Path("gadgets")
  public List<ItemResponse> gadgets() {
    return store.listGadgets().stream().map(ItemResponse::from).toList();
  }
}
