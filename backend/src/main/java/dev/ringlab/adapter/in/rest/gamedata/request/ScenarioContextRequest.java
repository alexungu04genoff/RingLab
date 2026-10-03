package dev.ringlab.adapter.in.rest.gamedata.request;

import dev.ringlab.domain.gamedata.ScenarioContext;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Validate JSON numbers before conversion; Integer coercion could silently truncate fractions. */
public record ScenarioContextRequest(
    @DecimalMin("1") @DecimalMax("3") @Digits(integer=1,fraction=0) BigDecimal lap,
    ScenarioContext.VehicleForm vehicleForm,
    @DecimalMin("0") @DecimalMax("999") @Digits(integer=3,fraction=0) BigDecimal ringsHeld,
    Boolean landingBoostActive,
    @DecimalMin("0") @DecimalMax("50000") @Digits(integer=5,fraction=0) BigDecimal distanceToFinish) {
  public ScenarioContext toDomain() {
    return new ScenarioContext(integer(lap), vehicleForm, integer(ringsHeld), landingBoostActive, integer(distanceToFinish));
  }
  private static Integer integer(BigDecimal value) { return value == null ? null : value.intValueExact(); }
}
