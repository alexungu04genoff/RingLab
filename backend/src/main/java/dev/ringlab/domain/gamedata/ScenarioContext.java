package dev.ringlab.domain.gamedata;

/** A snapshot chosen by the player, never inferred from a build or a recommended map. */
public record ScenarioContext(Integer lap, VehicleForm vehicleForm, Integer ringsHeld,
    Boolean landingBoostActive, Integer distanceToFinish) {
  public enum VehicleForm { NORMAL, WATER, FLIGHT }
  public static final ScenarioContext UNSPECIFIED = new ScenarioContext(null, null, null, null, null);

  public ScenarioContext {
    bounded(lap, 1, 3, "Lap");
    bounded(ringsHeld, 0, 999, "Rings held");
    bounded(distanceToFinish, 0, 50000, "Distance to finish");
  }

  private static void bounded(Integer value, int min, int max, String label) {
    if (value != null && (value < min || value > max))
      throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
  }
}
