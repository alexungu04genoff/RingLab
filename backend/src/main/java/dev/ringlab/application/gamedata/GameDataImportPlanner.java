package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import java.util.*;
import java.util.function.Function;

final class GameDataImportPlanner {
  GameDataImportPlan plan(GameDataSet current, GameDataSet desired) {
    var added = new Changes();
    var updated = new Changes();
    var changes = new ArrayList<String>();
    var unsafe = new ArrayList<String>();
    compare("racers", current.racers(), desired.racers(), Racer::id, CatalogFields::racer,
        Set.of("racing_type"), added.racers, updated.racers, changes, unsafe);
    compare("machines", current.machines(), desired.machines(), Machine::id, CatalogFields::machine,
        Set.of("racing_type"), added.machines, updated.machines, changes, unsafe);
    compare("machine-parts", current.parts(), desired.parts(), MachinePart::id, CatalogFields::part,
        Set.of("source_machine_id", "part_type"), added.parts, updated.parts, changes, unsafe);
    compare("gadgets", current.gadgets(), desired.gadgets(), Gadget::id, CatalogFields::gadget,
        Set.of("slot_cost"), added.gadgets, updated.gadgets, changes, unsafe);
    compare("maps", current.maps(), desired.maps(), RaceMap::id, CatalogFields::map,
        Set.of("category", "catalog_order"), added.maps, updated.maps, changes, unsafe);
    compare("game-versions", current.versions(), desired.versions(), GameVersion::id, CatalogFields::version,
        Set.of("version", "released_at"), added.versions, updated.versions, changes, unsafe);

    var oldSnapshots = indexSnapshots(current);
    var oldVersions = index(current.versions(), GameVersion::id);
    var labels = index(desired.versions(), GameVersion::id);
    for (var snapshot : desired.snapshots().stream().sorted(Comparator.comparing(s -> s.versionId().toString())).toList()) {
      String label = labels.get(snapshot.versionId()).value().version();
      if (oldVersions.containsKey(snapshot.versionId())) {
        var old = oldSnapshots.get(snapshot.versionId());
        // DB readers include empty snapshots too; absence is still an existing, published version.
        published("versions/" + label + "/racer-stats.csv", old == null ? List.of() : old.racers(), snapshot.racers(), unsafe);
        published("versions/" + label + "/machine-part-stats.csv", old == null ? List.of() : old.parts(), snapshot.parts(), unsafe);
      } else {
        complete(snapshot.racers(), desired.racers().stream().map(r -> r.value().id()).toList(),
            "versions/" + label + "/racer-stats.csv", unsafe);
        complete(snapshot.parts(), desired.parts().stream().map(r -> r.value().id()).toList(),
            "versions/" + label + "/machine-part-stats.csv", unsafe);
        added.snapshots.add(snapshot);
        changes.add("+ racer stats: " + snapshot.racers().size() + " rows for " + label);
        changes.add("+ machine-part stats: " + snapshot.parts().size() + " rows for " + label);
      }
    }
    return new GameDataImportPlan(added.dataset(), updated.dataset(), changes.stream().sorted().toList(),
        unsafe.stream().sorted().toList(), CatalogFingerprint.approval(current, desired));
  }

  private <T> void compare(String name, List<CatalogRow<T>> before, List<CatalogRow<T>> after,
      Function<T, UUID> id, Function<T, Map<String, Object>> fields, Set<String> immutable,
      List<CatalogRow<T>> additions, List<CatalogRow<T>> updates, List<String> changes, List<String> unsafe) {
    var previous = index(before, id);
    var next = index(after, id);
    for (var row : before) if (!next.containsKey(id.apply(row.value())))
      unsafe.add(row.problem("id", id.apply(row.value()), "Existing " + name + " identity omitted; deletion/UUID replacement is forbidden"));
    var previousNames = new HashMap<Object, UUID>();
    for (var row : before) {
      Object label = fields.apply(row.value()).get("name");
      if (label != null) previousNames.put(label, id.apply(row.value()));
    }
    for (var row : after) {
      UUID key = id.apply(row.value());
      var values = fields.apply(row.value());
      Object label = values.getOrDefault("name", values.getOrDefault("version", key));
      UUID owner = previousNames.get(values.get("name"));
      if (owner != null && !owner.equals(key))
        unsafe.add(row.problem("name", label, "Name already belongs to another existing UUID; simultaneous name swaps/reuse are unsupported"));
      var old = previous.get(key);
      if (old == null) {
        additions.add(row);
        changes.add("+ " + name + ": " + label + " [" + key + "]");
        continue;
      }
      var oldValues = fields.apply(old.value());
      boolean changed = false;
      for (var entry : values.entrySet()) {
        String field = entry.getKey();
        Object value = entry.getValue();
        if (Objects.equals(CatalogFingerprint.normalized(oldValues.get(field)), CatalogFingerprint.normalized(value))) continue;
        if (immutable.contains(field)) unsafe.add(row.problem(field, value, "Existing " + name + " field is immutable (was " + oldValues.get(field) + ")"));
        else {
          changes.add("~ " + name + " " + label + " [" + key + "] " + field + ": "
              + display(oldValues.get(field)) + " -> " + display(value));
          changed = true;
        }
      }
      if (changed) updates.add(row);
    }
  }

  private void published(String file, List<CatalogRow<StatRow>> before, List<CatalogRow<StatRow>> after, List<String> unsafe) {
    var previous = index(before, StatRow::itemId);
    var next = index(after, StatRow::itemId);
    for (var row : before) if (!next.containsKey(row.value().itemId()))
      unsafe.add(new CatalogRow<>(file, 1, row.value()).problem("id", row.value().itemId(), "Previously published stat row omitted"));
    for (var row : after) {
      var old = previous.get(row.value().itemId());
      if (old == null) {
        unsafe.add(row.problem("id", row.value().itemId(), "Cannot add stats to an existing published version"));
        continue;
      }
      var oldFields = CatalogFields.stats(old.value());
      CatalogFields.stats(row.value()).forEach((field, value) -> {
        if (!Objects.equals(CatalogFingerprint.normalized(oldFields.get(field)), CatalogFingerprint.normalized(value)))
          unsafe.add(row.problem(field, value, "Published stat value is immutable (was " + oldFields.get(field) + ")"));
      });
    }
  }

  private void complete(List<CatalogRow<StatRow>> rows, List<UUID> required, String file, List<String> unsafe) {
    var present = index(rows, StatRow::itemId);
    for (UUID id : required) if (!present.containsKey(id))
      unsafe.add(new CatalogRow<>(file, 1, id).problem("id", id, "New snapshot requires a row for every current identity; use blank stats for unknown values"));
  }

  private static String display(Object value) {
    return value == null ? "<unknown>" : '"' + value.toString().replace("\r", "\\r").replace("\n", "\\n") + '"';
  }

  private <T> Map<UUID, CatalogRow<T>> index(List<CatalogRow<T>> rows, Function<T, UUID> id) {
    var result = new LinkedHashMap<UUID, CatalogRow<T>>();
    rows.forEach(r -> result.put(id.apply(r.value()), r));
    return result;
  }

  private Map<UUID, VersionStats> indexSnapshots(GameDataSet data) {
    var result = new HashMap<UUID, VersionStats>();
    data.snapshots().forEach(s -> result.put(s.versionId(), s));
    return result;
  }

  private static final class Changes {
    final List<CatalogRow<Racer>> racers = new ArrayList<>();
    final List<CatalogRow<Machine>> machines = new ArrayList<>();
    final List<CatalogRow<MachinePart>> parts = new ArrayList<>();
    final List<CatalogRow<Gadget>> gadgets = new ArrayList<>();
    final List<CatalogRow<RaceMap>> maps = new ArrayList<>();
    final List<CatalogRow<GameVersion>> versions = new ArrayList<>();
    final List<VersionStats> snapshots = new ArrayList<>();
    GameDataSet dataset() { return new GameDataSet(racers, machines, parts, gadgets, maps, versions, snapshots); }
  }
}
