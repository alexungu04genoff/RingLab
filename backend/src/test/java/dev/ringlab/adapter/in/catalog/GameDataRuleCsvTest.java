package dev.ringlab.adapter.in.catalog;

import dev.ringlab.application.gamedata.GameDataImportService;
import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.domain.gamedata.importing.ImportValidationException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class GameDataRuleCsvTest {
  @TempDir Path directory;
  private final GameDataCsvReader reader = new GameDataCsvReader();
  private final GameDataImportService validation = new GameDataImportService(null);
  private static final String TUNER = "70000000-0000-4000-8000-000000000052";

  @BeforeEach void copyCanonical() throws Exception {
    Path source = Path.of("../game-data");
    try (var paths = Files.walk(source)) {
      for (var path : paths.toList()) {
        var destination = directory.resolve(source.relativize(path));
        if (Files.isDirectory(path)) Files.createDirectories(destination); else Files.copy(path, destination);
      }
    }
  }
  static Stream<Arguments> invalidFields() {
    return Stream.of(
        Arguments.of("passive-gadget-rules", "gadget_id", "broken"),
        Arguments.of("passive-gadget-rules", "gadget_id", "ffffffff-ffff-ffff-ffff-ffffffffffff"),
        Arguments.of("passive-gadget-rules", "effect_id", ""),
        Arguments.of("passive-gadget-rules", "effect_id", "other_0"),
        Arguments.of("passive-gadget-rules", "position", "-1"),
        Arguments.of("passive-gadget-rules", "position", "99"),
        Arguments.of("passive-gadget-rules", "kind", "ACTIVE"),
        Arguments.of("passive-gadget-rules", "subject", "PART"),
        Arguments.of("passive-gadget-rules", "required_racing_type", "SPEEDY"),
        Arguments.of("passive-gadget-rules", "required_racing_type", ""),
        Arguments.of("passive-gadget-rules", "matching_speed", "1,5"),
        Arguments.of("passive-gadget-rules", "matching_speed", ""),
        Arguments.of("passive-gadget-rules", "nonmatching_speed", ""),
        Arguments.of("passive-gadget-rules", "scenario_stat_potential", "yes"),
        Arguments.of("passive-gadget-rules", "scenario_stat_potential", "false"),
        Arguments.of("passive-gadget-rules", "stacking_group", "all-effects"),
        Arguments.of("scenario-gadget-rules", "effect_id", "missing"),
        Arguments.of("scenario-gadget-rules", "condition", "ALWAYS"),
        Arguments.of("scenario-gadget-rules", "speed", ""),
        Arguments.of("scenario-gadget-rules", "speed", "NaN"),
        Arguments.of("rule-sources", "effect_type", "OTHER"),
        Arguments.of("rule-sources", "effect_id", "missing"),
        Arguments.of("rule-sources", "url", "file:///tmp/evidence"),
        Arguments.of("rule-sources", "url", "https://user:secret@example.test/source"),
        Arguments.of("rule-sources", "url", "https://bad url"),
        Arguments.of("rule-sources", "position", "1.5"));
  }
  @ParameterizedTest @MethodSource("invalidFields")
  void rejectsBadFactsWithFileRowFieldAndValue(String file, String field, String value) throws Exception {
    edit(file, field, value);
    var error = assertThrows(ImportValidationException.class, () -> validation.validate(reader.read(directory)));
    assertTrue(error.getMessage().contains(file + ".csv:"), error.getMessage());
    assertTrue(error.getMessage().contains("field=")); assertTrue(error.getMessage().contains("value="));
  }
  @ParameterizedTest @ValueSource(strings = {"rule-set", "passive-gadget-rules", "scenario-gadget-rules", "rule-sources"})
  void partialBundlesCannotDisappearSilently(String file) throws Exception {
    Files.delete(path(file));
    assertThrows(ImportValidationException.class, () -> reader.read(directory));
  }
  @Test void exactDecimalsAndNullScenarioUtilityVectorsSurviveParsing() throws Exception {
    edit("passive-gadget-rules", "matching_speed", "20.1234567890123456789");
    var data = reader.read(directory); validation.validate(data);
    var rules = data.ruleSets().getFirst();
    assertEquals(new BigDecimal("20.1234567890123456789"), rules.passive().stream()
        .filter(r -> r.value().rule().gadgetId().toString().equals(TUNER)).findFirst().orElseThrow().value().rule().matching().speed());
    assertEquals(2, rules.scenario().stream().filter(r -> r.value().rule().adjustment() == null).count());
    assertEquals(BigDecimal.ZERO, rules.passive().getFirst().value().rule().matching().speed());
  }
  @ParameterizedTest @ValueSource(strings = {"passive-gadget-rules", "scenario-gadget-rules", "rule-sources", "rule-set"})
  void duplicateRowsAreRejected(String file) throws Exception {
    var lines = Files.readAllLines(path(file)); lines.add(lines.get(1)); Files.write(path(file), lines);
    assertThrows(ImportValidationException.class, () -> validation.validate(reader.read(directory)));
  }
  @Test void sourcesMustExistAndReferenceTheCorrectEffectType() throws Exception {
    Files.writeString(path("rule-sources"), "effect_type,gadget_id,effect_id,position,url\n");
    assertTrue(assertThrows(ImportValidationException.class, () -> validation.validate(reader.read(directory))).getMessage().contains("At least one source"));
  }
  @Test void utilityFactsCannotCarryPassiveNumbersOrOmitTheirConditionalClassification() throws Exception {
    edit("passive-gadget-rules", "kind", "CONDITIONAL");
    var error = assertThrows(ImportValidationException.class, () -> validation.validate(reader.read(directory)));
    assertTrue(error.getMessage().contains("Non-passive effects require zero"));
    assertTrue(error.getMessage().contains("Required true/false"));
  }
  @Test void csvRowOrderIsIndependentOfStableIdsAndExplicitPositions() throws Exception {
    for (var file : List.of("passive-gadget-rules", "scenario-gadget-rules", "rule-sources")) {
      var lines = Files.readAllLines(path(file)); Collections.reverse(lines.subList(1, lines.size())); Files.write(path(file), lines);
    }
    var data = reader.read(directory); validation.validate(data);
    assertEquals(dev.ringlab.importing.RuleFixtures.snapshot(), data.ruleSets().getFirst().snapshot(
        data.versions().stream().map(r -> r.value()).filter(v -> v.version().equals("1.4.1")).findFirst().orElseThrow()));
  }
  private Path path(String file) { return directory.resolve("versions/1.4.1/" + file + ".csv"); }
  private void edit(String file, String field, String value) throws Exception {
    List<CsvRecords.Record> records;
    try (var input = Files.newBufferedReader(path(file))) { records = CsvRecords.read(input, file); }
    var header = records.getFirst().fields();
    int index = file.equals("passive-gadget-rules") ? java.util.stream.IntStream.range(1, records.size())
        .filter(i -> records.get(i).fields().getFirst().equals(TUNER)).findFirst().orElseThrow() : 1;
    var lines = new ArrayList<String>();
    for (int i = 0; i < records.size(); i++) {
      var values = new ArrayList<>(records.get(i).fields());
      if (i == index) values.set(header.indexOf(field), value);
      lines.add(values.stream().map(v -> "\"" + v.replace("\"", "\"\"") + "\"").collect(java.util.stream.Collectors.joining(",")));
    }
    Files.write(path(file), lines);
  }
}
