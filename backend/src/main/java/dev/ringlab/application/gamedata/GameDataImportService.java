package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.port.in.ImportGameDataUseCase;
import dev.ringlab.port.out.GameDataImportRepository;
import java.util.List;

/** Explicit command use case; never registered as an application startup observer. */
public final class GameDataImportService implements ImportGameDataUseCase {
  private final GameDataImportRepository repository;
  private final GameDataSetValidator validator = new GameDataSetValidator();
  private final GameDataImportPlanner planner = new GameDataImportPlanner();

  public GameDataImportService(GameDataImportRepository repository) { this.repository = repository; }

  @Override public void validate(GameDataSet dataset) { validator.validate(dataset); }

  @Override public GameDataImportPlan plan(GameDataSet dataset) {
    validate(dataset);
    return planner.plan(repository.read(), dataset);
  }

  @Override public GameDataImportPlan apply(GameDataSet dataset, String approvedToken) {
    validate(dataset);
    return repository.apply(current -> {
      var plan = planner.plan(current, dataset);
      if (!plan.safe()) throw new ImportValidationException(plan.unsafeChanges());
      if (!plan.approvalToken().equals(approvedToken))
        throw new ImportValidationException(List.of("Approval token does not match the current files/database. Run plan again and review it before apply."));
      return plan;
    });
  }
}
