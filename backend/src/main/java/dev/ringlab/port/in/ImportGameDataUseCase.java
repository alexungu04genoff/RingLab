package dev.ringlab.port.in;

import dev.ringlab.domain.gamedata.importing.GameDataImportPlan;
import dev.ringlab.domain.gamedata.importing.GameDataSet;

public interface ImportGameDataUseCase {
  void validate(GameDataSet dataset);
  GameDataImportPlan plan(GameDataSet dataset);
  GameDataImportPlan apply(GameDataSet dataset, String approvedToken);
}
