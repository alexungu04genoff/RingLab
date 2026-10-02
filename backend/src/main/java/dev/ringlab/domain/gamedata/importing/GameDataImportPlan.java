package dev.ringlab.domain.gamedata.importing;

import java.util.List;

/** Only explicitly approved inserts and presentation updates are in changes. */
public record GameDataImportPlan(GameDataSet additions, GameDataSet updates,
    List<String> changes, List<String> unsafeChanges, String approvalToken) {
  public GameDataImportPlan {
    changes = List.copyOf(changes);
    unsafeChanges = List.copyOf(unsafeChanges);
  }

  public boolean safe() { return unsafeChanges.isEmpty(); }
  public int inserts() { return count(additions); }
  public int updateCount() { return count(updates); }

  private static int count(GameDataSet data) {
    return data.racers().size() + data.machines().size() + data.parts().size()
        + data.gadgets().size() + data.maps().size() + data.versions().size()
        + data.snapshots().stream().mapToInt(s -> s.racers().size() + s.parts().size()).sum()
        + data.ruleSets().stream().mapToInt(GameDataRuleSet::rowCount).sum();
  }
}
