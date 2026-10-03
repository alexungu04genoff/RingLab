package dev.ringlab.adapter.in.catalog;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.domain.gamedata.importing.ImportValidationException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GameDataCsvReaderTest {
  @TempDir Path directory;

  @Test void quotedCsvHandlesCommasEscapesBomAndPhysicalLineNumbers() throws Exception {
    var rows = CsvRecords.read(new StringReader("\ufeffid,name\r\n1,\"A, \"\"quoted\"\"\r\nname\"\r\n2,last"), "test.csv");
    assertEquals(List.of("1", "A, \"quoted\"\nname"), rows.get(1).fields());
    assertEquals(4, rows.get(2).line());
    assertEquals(List.of("2", "last"), rows.get(2).fields());
    assertEquals(List.of("", ""), CsvRecords.read(new StringReader(","), "test.csv").getFirst().fields());
    assertEquals(List.of(""), CsvRecords.read(new StringReader("\"\""), "test.csv").getFirst().fields());
  }

  @ParameterizedTest @ValueSource(strings = {"a,\"open", "a,b\"c", "a,\"closed\"oops", "a,\u0000"})
  void malformedCsvFailsWithLocation(String input) {
    var error = assertThrows(ImportValidationException.class, () -> CsvRecords.read(new StringReader(input), "broken.csv"));
    assertTrue(error.getMessage().contains("broken.csv:1 field=csv"));
  }

  @Test void currentFilesPreserveCountsIdentitiesDecimalsAndNullArtwork() {
    var d = new GameDataCsvReader().read(Path.of("../game-data"));
    assertEquals(53, d.racers().size()); assertEquals(63, d.machines().size());
    assertEquals(177, d.parts().size()); assertEquals(117, d.gadgets().size());
    assertEquals(44, d.maps().size()); assertEquals(4, d.versions().size());
    assertEquals(209, d.snapshots().stream().mapToInt(s -> s.racers().size()).sum());
    assertEquals(696, d.snapshots().stream().mapToInt(s -> s.parts().size()).sum());
    assertEquals(49, d.gadgets().stream().filter(r -> r.value().imagePath() == null).count());
    assertTrue(d.snapshots().stream().flatMap(s -> s.parts().stream()).anyMatch(r -> r.value().stats().acceleration().scale() > 0));
  }

  @Test void exactDecimalBlankAndZeroAreDistinct() throws Exception {
    copyDataset();
    Path file = directory.resolve("versions/1.4.1/racer-stats.csv");
    var lines = Files.readAllLines(file);
    String id = lines.get(1).split(",")[0];
    lines.set(1, id + ",17.50000000000000000001,,0,-2,0.00");
    Files.write(file, lines);
    var stats = new GameDataCsvReader().read(directory).snapshots().stream()
        .filter(s -> s.versionId().toString().endsWith("001")).findFirst().orElseThrow().racers().getFirst().value().stats();
    assertEquals(new BigDecimal("17.50000000000000000001"), stats.speed());
    assertNull(stats.acceleration()); assertEquals(BigDecimal.ZERO, stats.handling());
    assertEquals(new BigDecimal("-2"), stats.power()); assertEquals(new BigDecimal("0.00"), stats.boost());
  }

  @ParameterizedTest @ValueSource(strings = {
      "id,name,racing_type,image_path,image_path\n",
      "id,name,racing_type,extra\n",
      "id,name,racing_type,image_path\nnot-a-uuid,Name,SPEED,\n",
      "id,name,racing_type,image_path\n1-1-1-1-1,Name,SPEED,\n",
      "id,name,racing_type,image_path\n013ecae2-55b9-58bb-b1c5-b054578058ff,Name,FAST,\n",
      "id,name,racing_type,image_path\n013ecae2-55b9-58bb-b1c5-b054578058ff,,SPEED,\n",
      "id,name,racing_type,image_path\nmissing,columns\n", ""})
  void rejectsBadHeadersAndFields(String csv) throws Exception {
    copyDataset(); Files.writeString(directory.resolve("catalog/racers.csv"), csv);
    var error = assertThrows(ImportValidationException.class, () -> new GameDataCsvReader().read(directory));
    assertTrue(error.getMessage().contains("catalog/racers.csv:"));
    assertTrue(error.getMessage().contains("field=")); assertTrue(error.getMessage().contains("value="));
  }

  @ParameterizedTest @ValueSource(strings = {"17x", "\"17,5\"", "NaN", "1e3", "NULL"})
  void invalidDecimalsAreNotSilentlyUnknown(String value) throws Exception {
    copyDataset();
    Files.writeString(directory.resolve("versions/1.4.1/racer-stats.csv"),
        "racer_id,speed,acceleration,handling,power,boost\n013ecae2-55b9-58bb-b1c5-b054578058ff,20," + value + ",0,0,0\n");
    var error = assertThrows(ImportValidationException.class, () -> new GameDataCsvReader().read(directory));
    assertTrue(error.getMessage().contains("racer-stats.csv:2 field=acceleration"));
  }

  @ParameterizedTest @ValueSource(strings = {
      "catalog/machines.csv|racing_type|SPEED|BAD", "catalog/machine-parts.csv|part_type|FRONT|WING",
      "catalog/maps.csv|category|MAIN_COURSE|ROAD", "catalog/maps.csv|catalog_order|1|2147483648",
      "catalog/gadgets.csv|slot_cost|1|1.5", "catalog/game-versions.csv|released_at|2025-12-03|2025-02-30",
      "catalog/game-versions.csv|version|1.2.0|../outside"})
  void typedColumnsRejectBadValues(String spec) throws Exception {
    copyDataset(); var parts = spec.split("\\|");
    Path file = directory.resolve(parts[0]);
    List<CsvRecords.Record> records;
    try (var reader = Files.newBufferedReader(file)) { records = CsvRecords.read(reader, parts[0]); }
    int column = records.getFirst().fields().indexOf(parts[1]);
    var record = records.stream().skip(1).filter(r -> r.fields().get(column).equals(parts[2])).findFirst().orElseThrow();
    var values = new java.util.ArrayList<>(record.fields()); values.set(column, parts[3]);
    String csvRow = String.join(",", values.stream().map(v -> "\"" + v.replace("\"", "\"\"") + "\"").toList());
    Files.writeString(file, String.join(",", records.getFirst().fields()) + "\n" + csvRow + "\n");
    var error = assertThrows(ImportValidationException.class, () -> new GameDataCsvReader().read(directory));
    assertTrue(error.getMessage().contains("field=" + parts[1]));
  }

  @Test void requiredFilesAndUndeclaredVersionFoldersFail() throws Exception {
    copyDataset(); Files.delete(directory.resolve("versions/1.3.1/racer-stats.csv"));
    Files.createDirectories(directory.resolve("versions/forgot-to-declare"));
    var error = assertThrows(ImportValidationException.class, () -> new GameDataCsvReader().read(directory));
    assertTrue(error.getMessage().contains("Missing or unreadable")); assertTrue(error.getMessage().contains("Not declared"));
  }

  private void copyDataset() throws Exception {
    Path root = Path.of("../game-data");
    try (var files = Files.walk(root)) {
      for (var file : files.filter(Files::isRegularFile).toList()) {
        Path dest = directory.resolve(root.relativize(file));
        Files.createDirectories(dest.getParent()); Files.copy(file, dest);
      }
    }
  }
}
