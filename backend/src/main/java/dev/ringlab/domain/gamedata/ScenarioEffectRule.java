package dev.ringlab.domain.gamedata;

import java.util.List;
import java.util.UUID;

/** Fixed reviewed snapshot effects only; accumulating effects await complete evidence. */
public record ScenarioEffectRule(UUID gadgetId, String effectId, String label, Condition condition,
    BaseStats adjustment, String explanation, List<String> sources) {
  public enum Field { LAP, VEHICLE_FORM, RINGS_HELD, LANDING_BOOST_ACTIVE, DISTANCE_TO_FINISH }
  public enum Condition {
    LAP_ONE(Field.LAP), LAP_THREE(Field.LAP), WATER(Field.VEHICLE_FORM),
    FLIGHT(Field.VEHICLE_FORM), TRANSFORMED(Field.VEHICLE_FORM),
    HAS_RINGS(Field.RINGS_HELD), LANDING_BOOST(Field.LANDING_BOOST_ACTIVE),
    FINISH_ZONE(Field.DISTANCE_TO_FINISH);
    private final Field field;
    Condition(Field field) { this.field = field; }
    public Field field() { return field; }

    /** null deliberately means unknown, not false. */
    public Boolean matches(ScenarioContext context) {
      return switch (this) {
        case LAP_ONE -> context.lap() == null ? null : context.lap() == 1;
        case LAP_THREE -> context.lap() == null ? null : context.lap() == 3;
        case WATER -> context.vehicleForm() == null ? null : context.vehicleForm() == ScenarioContext.VehicleForm.WATER;
        case FLIGHT -> context.vehicleForm() == null ? null : context.vehicleForm() == ScenarioContext.VehicleForm.FLIGHT;
        case TRANSFORMED -> context.vehicleForm() == null ? null : context.vehicleForm() != ScenarioContext.VehicleForm.NORMAL;
        case HAS_RINGS -> context.ringsHeld() == null ? null : context.ringsHeld() > 0;
        case LANDING_BOOST -> context.landingBoostActive();
        case FINISH_ZONE -> context.distanceToFinish() == null ? null : context.distanceToFinish() <= 300;
      };
    }
  }
  public ScenarioEffectRule { sources = List.copyOf(sources); }
  public boolean statEffect() { return adjustment != null; }
}
