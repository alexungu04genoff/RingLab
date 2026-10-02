package dev.ringlab.adapter.in.catalog;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.port.in.ImportGameDataUseCase;
import dev.ringlab.importing.ImportFixture;
import java.io.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameDataImportCommandTest {
  private final Stub imports = new Stub();
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private int run(String... args) { return new GameDataImportCommand(imports).run(args, new PrintStream(bytes), new PrintStream(bytes)); }

  @Test void validateReportsCountsWithoutPlanning() {
    assertEquals(0, run("validate", "../game-data")); assertTrue(bytes.toString().contains("52 racers")); assertFalse(imports.planned);
  }
  @Test void planAndApplyReportNoOpAndApprovalToken() {
    assertEquals(0, run("plan", "../game-data")); assertTrue(bytes.toString().contains("Approval token: token"));
    assertTrue(bytes.toString().contains("READY TO APPLY (no-op)"));
    assertEquals(0, run("apply", "../game-data", "--approve", "token")); assertTrue(bytes.toString().contains("NO-OP"));
  }
  @Test void writesAndUnsafePlansAreClearlyDifferent() {
    imports.result = new GameDataImportPlan(ImportFixture.sample(), ImportFixture.empty(), List.of("+ test data"), List.of(), "token");
    assertEquals(0, run("apply", "../game-data", "--approve", "token")); assertTrue(bytes.toString().contains("APPLIED"));
    imports.result = new GameDataImportPlan(ImportFixture.empty(), ImportFixture.empty(), List.of(), List.of("Unsafe change"), "token");
    assertEquals(2, run("plan", "../game-data")); assertTrue(bytes.toString().contains("NOT SAFE TO APPLY"));
  }
  @ParameterizedTest @ValueSource(strings = {"", "validate", "unknown ../game-data", "apply ../game-data", "apply ../game-data --wrong token", "plan ../game-data extra"})
  void rejectsInvalidCommandLines(String command) { assertEquals(2, run(command.isEmpty() ? new String[0] : command.split(" "))); }
  @Test void validationAndDatabaseFailuresReturnNonzero() {
    assertEquals(2, run("validate", "missing-directory")); assertTrue(bytes.toString().contains("Nothing was imported"));
    imports.failure = new IllegalStateException("Database failure");
    assertEquals(1, run("plan", "../game-data"));
  }

  private static final class Stub implements ImportGameDataUseCase {
    boolean planned; RuntimeException failure;
    GameDataImportPlan result = new GameDataImportPlan(ImportFixture.empty(), ImportFixture.empty(), List.of(), List.of(), "token");
    public void validate(GameDataSet d) {}
    public GameDataImportPlan plan(GameDataSet d) { planned = true; if (failure != null) throw failure; return result; }
    public GameDataImportPlan apply(GameDataSet d, String token) { assertEquals("token", token); return result; }
  }
}
