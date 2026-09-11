package dev.ringlab.adapter.in.rest.build.request;

import dev.ringlab.application.build.BuildService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BuildRequest(
    @NotBlank @Size(max = 120) String title,
    @NotNull @Size(max = 10000) String description,
    @NotNull UUID racerId,
    @NotNull UUID frontPartId,
    @NotNull UUID rearPartId,
    @NotNull UUID tirePartId,
    UUID gameVersionId,
    @NotNull List<@NotNull UUID> gadgetIds) {
  public BuildService.Draft draft() {
    return new BuildService.Draft(title, description, racerId, frontPartId, rearPartId, tirePartId, gameVersionId, gadgetIds);
  }
}
