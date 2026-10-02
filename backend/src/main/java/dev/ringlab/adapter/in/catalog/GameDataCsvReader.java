package dev.ringlab.adapter.in.catalog;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

/** External syntax only. Cross-record rules are validated by the input use case. */
public final class GameDataCsvReader {
  public GameDataSet read(Path root) {
    var errors = new ArrayList<String>();
    var racers = rows(root, "catalog/racers.csv", "id,name,racing_type,image_path", r ->
        new Racer(r.uuid("id"), r.text("name", 100, false), r.type("racing_type", RacingType.class, true),
            r.text("image_path", 255, true)), errors);
    var machines = rows(root, "catalog/machines.csv", "id,name,racing_type,image_path", r ->
        new Machine(r.uuid("id"), r.text("name", 100, false), r.type("racing_type", RacingType.class, true),
            r.text("image_path", 255, true)), errors);
    var parts = rows(root, "catalog/machine-parts.csv", "id,source_machine_id,part_type", r ->
        new MachinePart(r.uuid("id"), r.uuid("source_machine_id"), r.type("part_type", MachinePartType.class, false)), errors);
    var gadgets = rows(root, "catalog/gadgets.csv", "id,name,description,slot_cost,image_path", r ->
        new Gadget(r.uuid("id"), r.text("name", 100, false), r.text("description", Integer.MAX_VALUE, true),
            r.integer("slot_cost", true), r.text("image_path", 255, true)), errors);
    var maps = rows(root, "catalog/maps.csv", "id,name,category,content_pack,image_path,catalog_order", r ->
        new RaceMap(r.uuid("id"), r.text("name", 120, false), r.type("category", RaceMap.Category.class, false),
            r.text("content_pack", 120, true), r.text("image_path", 255, true), r.integer("catalog_order", false)), errors);
    var versions = rows(root, "catalog/game-versions.csv", "id,version,released_at", r ->
        new GameVersion(r.uuid("id"), r.version(), r.date("released_at")), errors);
    var snapshots = new ArrayList<VersionStats>();
    for (var row : versions) {
      var version = row.value();
      String folder = "versions/" + version.version() + "/";
      snapshots.add(new VersionStats(version.id(),
          rows(root, folder + "racer-stats.csv", "racer_id,speed,acceleration,handling,power,boost",
              r -> new StatRow(r.uuid("racer_id"), r.stats()), errors),
          rows(root, folder + "machine-part-stats.csv", "machine_part_id,speed,acceleration,handling,power,boost",
              r -> new StatRow(r.uuid("machine_part_id"), r.stats()), errors)));
    }
    if (Files.isDirectory(root.resolve("versions"))) {
      try (var folders = Files.list(root.resolve("versions"))) {
        var names = versions.stream().map(r -> r.value().version()).toList();
        folders.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted()
            .filter(n -> !names.contains(n)).forEach(n -> errors.add("versions/" + n
                + ":1 field=version value=\"" + n + "\": Not declared in catalog/game-versions.csv"));
      } catch (IOException e) { errors.add("versions:1 field=file value=\"versions\": Cannot list directory"); }
    }
    if (!errors.isEmpty()) throw new ImportValidationException(errors);
    return new GameDataSet(racers, machines, parts, gadgets, maps, versions, snapshots);
  }

  private <T> List<CatalogRow<T>> rows(Path root, String file, String columns,
                                      Function<Row, T> parse, List<String> errors) {
    var values = new ArrayList<CatalogRow<T>>();
    try (var reader = Files.newBufferedReader(root.resolve(file), StandardCharsets.UTF_8)) {
      var records = CsvRecords.read(reader, file);
      if (records.isEmpty()) throw failure(file, 1, "header", "", "Missing CSV header");
      var header = records.getFirst().fields();
      if (new HashSet<>(header).size() != header.size())
        throw failure(file, 1, "header", header, "Duplicate column");
      if (!new HashSet<>(header).equals(new HashSet<>(List.of(columns.split(",")))))
        throw failure(file, 1, "header", header, "Expected exactly these columns: " + columns);
      for (var record : records.subList(1, records.size())) {
        try {
          if (record.fields().size() != header.size())
            throw failure(file, record.line(), "csv", record.fields(), "Wrong number of fields");
          var fields = new HashMap<String, String>();
          for (int i = 0; i < header.size(); i++) fields.put(header.get(i), record.fields().get(i));
          values.add(new CatalogRow<>(file, record.line(), parse.apply(new Row(file, record.line(), fields))));
        } catch (ImportValidationException e) { errors.addAll(e.problems()); }
      }
    } catch (ImportValidationException e) { errors.addAll(e.problems()); }
    catch (IOException e) { errors.add(file + ":1 field=file value=\"" + file + "\": Missing or unreadable UTF-8 CSV file"); }
    return values;
  }

  private static ImportValidationException failure(String file, int row, String field, Object value, String reason) {
    return new ImportValidationException(List.of(new CatalogRow<>(file, row, value).problem(field, value, reason)));
  }

  private record Row(String file, int line, Map<String, String> fields) {
    private String text(String field, int length, boolean nullable) {
      String value = fields.get(field);
      if (value.isEmpty() && nullable) return null;
      if (value.isBlank() || value.length() > length)
        throw bad(field, "Expected nonblank text with at most " + length + " characters");
      return value;
    }
    private UUID uuid(String field) {
      String value = fields.get(field);
      if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        throw bad(field, "Expected a complete UUID");
      return UUID.fromString(value);
    }
    private <E extends Enum<E>> E type(String field, Class<E> type, boolean nullable) {
      if (nullable && fields.get(field).isEmpty()) return null;
      try { return Enum.valueOf(type, fields.get(field)); }
      catch (IllegalArgumentException e) { throw bad(field, "Expected " + Arrays.toString(type.getEnumConstants())); }
    }
    private Integer integer(String field, boolean nullable) {
      String value = fields.get(field);
      if (nullable && value.isEmpty()) return null;
      try {
        if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
        return Integer.valueOf(value);
      } catch (NumberFormatException e) { throw bad(field, "Expected a 32-bit integer"); }
    }
    private BigDecimal decimal(String field) {
      String value = fields.get(field);
      if (value.isBlank()) return null;
      if (!value.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw bad(field, "Expected decimal using \".\"; blank means unknown");
      return new BigDecimal(value);
    }
    private LocalDate date(String field) {
      try { return LocalDate.parse(fields.get(field)); }
      catch (DateTimeParseException e) { throw bad(field, "Expected a valid ISO date YYYY-MM-DD"); }
    }
    private String version() {
      String value = text("version", 32, false);
      if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) throw bad("version", "Expected a version usable as one directory name");
      return value;
    }
    private BaseStats stats() {
      return new BaseStats(decimal("speed"), decimal("acceleration"), decimal("handling"), decimal("power"), decimal("boost"));
    }
    private ImportValidationException bad(String field, String reason) {
      return failure(file, line, field, fields.get(field), reason);
    }
  }
}
