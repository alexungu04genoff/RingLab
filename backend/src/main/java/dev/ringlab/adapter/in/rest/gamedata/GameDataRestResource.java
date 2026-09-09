package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.port.out.GameDataRepository;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class GameDataRestResource {
  private final GameDataRepository repository;

  public GameDataRestResource(GameDataRepository repository) {
    this.repository = repository;
  }

  @GET
  @Path("racers")
  public List<RacerResponse> racers() {
    return repository.listRacers().stream().map(RacerResponse::from).toList();
  }

  @GET
  @Path("machines")
  public List<MachineResponse> machines() {
    return repository.listMachines().stream().map(MachineResponse::from).toList();
  }

  @GET
  @Path("gadgets")
  public List<GadgetResponse> gadgets() {
    return repository.listGadgets().stream().map(GadgetResponse::from).toList();
  }
}
