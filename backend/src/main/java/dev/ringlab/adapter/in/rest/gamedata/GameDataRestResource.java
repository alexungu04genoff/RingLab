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
  public record RacerResponse(UUID id, String name, String racingType, String imagePath) {
    public static RacerResponse from(Racer racer) {
      return new RacerResponse(racer.id(), racer.name(), racer.racingType(), racer.imagePath());
    }
  }

  public record MachineResponse(UUID id, String name, String racingType, String imagePath) {
    public static MachineResponse from(Machine machine) {
      return new MachineResponse(
          machine.id(), machine.name(), machine.racingType(), machine.imagePath());
    }
  }

  public record GadgetResponse(
      UUID id, String name, String description, Integer slotCost, String imagePath) {
    public static GadgetResponse from(Gadget gadget) {
      return new GadgetResponse(
          gadget.id(), gadget.name(), gadget.description(), gadget.slotCost(), gadget.imagePath());
    }
  }

  private final GameDataStore store;

  public GameDataRestResource(GameDataStore store) {
    this.store = store;
  }

  @GET
  @Path("racers")
  public List<RacerResponse> racers() {
    return store.listRacers().stream().map(RacerResponse::from).toList();
  }

  @GET
  @Path("machines")
  public List<MachineResponse> machines() {
    return store.listMachines().stream().map(MachineResponse::from).toList();
  }

  @GET
  @Path("gadgets")
  public List<GadgetResponse> gadgets() {
    return store.listGadgets().stream().map(GadgetResponse::from).toList();
  }
}
