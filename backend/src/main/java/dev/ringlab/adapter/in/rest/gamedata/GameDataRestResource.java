package dev.ringlab.adapter.in.rest.gamedata;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.in.rest.gamedata.response.GadgetResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachineResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachinePartResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.RacerResponse;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class GameDataRestResource {
  private final GameDataRepository repository;

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

  @GET
  @Path("machine-parts")
  public List<MachinePartResponse> machineParts() {
    return repository.listMachineParts().stream()
        .map(part -> MachinePartResponse.from(part, repository.findMachine(part.sourceMachineId()).orElseThrow()))
        .sorted(Comparator.comparing(MachinePartResponse::sourceMachineName)
            .thenComparing(MachinePartResponse::type))
        .toList();
  }
}
