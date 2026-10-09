package dev.ringlab.adapter.in.rest.build.request;

import dev.ringlab.port.in.BuildUseCase;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import dev.ringlab.application.ValidationException;
import java.util.ArrayList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Setter presence distinguishes omitted recommendations from explicit JSON null/empty. */
public class BuildRequest {
  @NotBlank @Size(max = 120) public String title;
  @NotNull @Size(max = 10000) public String description;
  @NotNull public UUID racerId;
  @NotNull public UUID frontPartId;
  @NotNull public UUID rearPartId;
  public UUID tirePartId;
  public UUID gameVersionId;
  public UUID remixedFromBuildId;
  public java.time.Instant expectedUpdatedAt;
  @NotNull public List<@NotNull UUID> gadgetIds;
  private List<UUID> recommendedMapIds;
  private dev.ringlab.domain.build.BuildVisibility visibility;

  @JsonSetter("visibility")
  public void readVisibility(JsonNode value) {
    if (value == null || !value.isTextual())
      throw new ValidationException("visibility must be PRIVATE or PUBLIC", "visibility");
    try {
      visibility = dev.ringlab.domain.build.BuildVisibility.valueOf(value.textValue());
    } catch (IllegalArgumentException invalid) {
      throw new ValidationException("visibility must be PRIVATE or PUBLIC", "visibility");
    }
  }

  @JsonSetter("recommendedMapIds")
  public void readRecommendedMapIds(JsonNode value) {
    if (value == null || !value.isArray())
      throw new ValidationException("recommendedMapIds must be an array", "recommendedMapIds");
    var ids = new ArrayList<UUID>();
    for (var item : value) {
      if (!item.isTextual() || !item.textValue().matches(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        throw new ValidationException("Map IDs must be UUID strings", "recommendedMapIds");
      ids.add(UUID.fromString(item.textValue()));
    }
    recommendedMapIds = List.copyOf(ids);
  }

  public BuildUseCase.Draft draft() {
    return new BuildUseCase.Draft(title, description, racerId, frontPartId, rearPartId, tirePartId,
        gameVersionId, remixedFromBuildId, gadgetIds, recommendedMapIds, visibility, expectedUpdatedAt);
  }
}
