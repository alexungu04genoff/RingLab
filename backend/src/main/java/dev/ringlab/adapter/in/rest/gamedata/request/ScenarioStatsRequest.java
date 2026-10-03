package dev.ringlab.adapter.in.rest.gamedata.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record ScenarioStatsRequest(UUID gameVersionId, UUID racerId, UUID frontPartId, UUID rearPartId,
    UUID tirePartId, @NotNull @Size(max=6) List<@NotNull UUID> gadgetIds,
    @NotNull @Valid ScenarioContextRequest scenario) {}
