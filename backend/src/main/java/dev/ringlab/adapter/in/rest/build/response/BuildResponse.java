package dev.ringlab.adapter.in.rest.build.response;

import dev.ringlab.adapter.in.rest.gamedata.response.GadgetResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GameVersionResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachinePartResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.RacerResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BuildResponse(
    UUID id,
    String title,
    String description,
    AuthorResponse author,
    RacerResponse racer,
    MachinePartResponse frontPart,
    MachinePartResponse rearPart,
    MachinePartResponse tirePart,
    GameVersionResponse gameVersion,
    List<GadgetResponse> gadgets,
    Instant createdAt,
    Instant updatedAt,
    long score,
    long upvotes,
    long downvotes) {}
