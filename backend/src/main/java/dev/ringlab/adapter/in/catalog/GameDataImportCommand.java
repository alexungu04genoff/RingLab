package dev.ringlab.adapter.in.catalog;

import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.port.in.ImportGameDataUseCase;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;

public final class GameDataImportCommand {
  private final ImportGameDataUseCase imports;

  public GameDataImportCommand(ImportGameDataUseCase imports) { this.imports = imports; }

  public int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length < 2 || !Set.of("validate", "plan", "apply").contains(args[0])
        || (args[0].equals("apply") ? args.length != 4 || !args[2].equals("--approve") : args.length != 2)) {
      err.println("Usage: GameDataImportMain validate|plan <game-data-directory>");
      err.println("       GameDataImportMain apply <game-data-directory> --approve <token-from-plan>");
      return 2;
    }
    try {
      var dataset = new GameDataCsvReader().read(Path.of(args[1]));
      if (args[0].equals("validate")) {
        imports.validate(dataset);
        out.printf("VALID: %d racers, %d machines, %d parts, %d gadgets, %d maps, %d versions%n",
            dataset.racers().size(), dataset.machines().size(), dataset.parts().size(),
            dataset.gadgets().size(), dataset.maps().size(), dataset.versions().size());
        out.println("Offline validation only; run plan to check database compatibility and new snapshot completeness.");
        return 0;
      }
      var plan = args[0].equals("plan") ? imports.plan(dataset) : imports.apply(dataset, args[3]);
      out.println("Game-data import plan");
      out.println("Dataset versions: " + String.join(", ", dataset.versions().stream().map(v -> v.value().version()).sorted().toList()));
      plan.changes().forEach(out::println);
      out.println("Unsafe/destructive changes:");
      if (plan.safe()) out.println("  none"); else plan.unsafeChanges().forEach(out::println);
      out.printf("Database writes: %d inserts, %d updates, 0 deletes%n", plan.inserts(), plan.updateCount());
      if (!plan.safe()) { out.println("NOT SAFE TO APPLY — no writes performed"); return 2; }
      if (args[0].equals("apply")) out.println(plan.inserts() + plan.updateCount() == 0 ? "NO-OP: database already matches" : "APPLIED: transaction committed");
      else {
        out.println("Approval token: " + plan.approvalToken());
        out.println("READY TO APPLY" + (plan.inserts() + plan.updateCount() == 0 ? " (no-op)" : ""));
      }
      return 0;
    } catch (ImportValidationException e) {
      e.problems().forEach(err::println);
      err.println("NOT SAFE TO APPLY. Nothing was imported.");
      return 2;
    } catch (IllegalArgumentException | IllegalStateException e) {
      err.println(e.getMessage());
      return 1;
    }
  }
}
