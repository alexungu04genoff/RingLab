package dev.ringlab.adapter.in.rest.gamedata.request;

import dev.ringlab.domain.gamedata.ScenarioContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ScenarioStatsRequest(UUID gameVersionId, UUID racerId, UUID frontPartId, UUID rearPartId,
    UUID tirePartId, @NotNull @Size(max=6) List<@NotNull UUID> gadgetIds, @NotNull @Valid Context scenario) {
  // Validate the JSON number before conversion: Jackson's default Integer coercion truncates fractions.
  public record Context(@DecimalMin("1") @DecimalMax("3") @Digits(integer=1,fraction=0) BigDecimal lap,
      ScenarioContext.VehicleForm vehicleForm,
      @DecimalMin("0") @DecimalMax("999") @Digits(integer=3,fraction=0) BigDecimal ringsHeld, Boolean landingBoostActive,
      @DecimalMin("0") @DecimalMax("50000") @Digits(integer=5,fraction=0) BigDecimal distanceToFinish) {
    public ScenarioContext toDomain() {
      return new ScenarioContext(integer(lap), vehicleForm, integer(ringsHeld), landingBoostActive, integer(distanceToFinish));
    }
    private static Integer integer(BigDecimal value) { return value == null ? null : value.intValueExact(); }
  }
}
