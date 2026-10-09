package dev.ringlab.application.gamedata;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import java.util.*;
import java.util.function.Function;

final class GameDataSetValidator {
  void validate(GameDataSet data) {
    var errors = new ArrayList<String>();
    unique(data.racers(), Racer::id, "id", errors);
    unique(data.racers(), Racer::name, "name", errors);
    unique(data.machines(), Machine::id, "id", errors);
    unique(data.machines(), Machine::name, "name", errors);
    unique(data.parts(), MachinePart::id, "id", errors);
    unique(data.parts(), p -> p.sourceMachineId() + ":" + p.type(), "source_machine_id/part_type", errors);
    unique(data.gadgets(), Gadget::id, "id", errors);
    unique(data.gadgets(), Gadget::name, "name", errors);
    unique(data.maps(), RaceMap::id, "id", errors);
    unique(data.maps(), RaceMap::name, "name", errors);
    unique(data.maps(), RaceMap::catalogOrder, "catalog_order", errors);
    unique(data.versions(), GameVersion::id, "id", errors);
    unique(data.versions(), GameVersion::version, "version", errors);
    var machines = ids(data.machines(), Machine::id);
    for (var row : data.parts()) {
      if (!machines.contains(row.value().sourceMachineId()))
        errors.add(row.problem("source_machine_id", row.value().sourceMachineId(), "Referenced machine does not exist"));
    }
    for (var row : data.machines()) {
      var machine = row.value();
      var actual = data.parts().stream().map(CatalogRow::value)
          .filter(p -> p.sourceMachineId().equals(machine.id())).map(MachinePart::type).toList();
      if (machine.racingType() == null) {
        errors.add(row.problem("racing_type", null, "Machine composition cannot be verified without a racing type"));
      } else if (!new HashSet<>(actual).equals(new HashSet<>(MachineComposition.requiredSlots(machine.racingType())))
          || actual.size() != MachineComposition.requiredSlots(machine.racingType()).size()) {
        errors.add(row.problem("id", machine.id(), "Required machine parts: " + MachineComposition.requiredSlots(machine.racingType())));
      }
    }
    for (var row : data.gadgets()) {
      var gadget = row.value();
      if (gadget.acquisitionKind() == null)
        errors.add(row.problem("acquisition_kind", null, "Reviewed acquisition kind is required; use UNKNOWN when unresolved"));
      if (gadget.acquisitionKind() == GadgetAcquisitionKind.FESTIVAL_REWARD
          && (gadget.acquisitionLabel() == null || gadget.acquisitionLabel().isBlank()))
        errors.add(row.problem("acquisition_label", gadget.acquisitionLabel(), "Festival reward requires its reviewed event label"));
      if (gadget.acquisitionKind() == GadgetAcquisitionKind.UNKNOWN && gadget.acquisitionLabel() != null)
        errors.add(row.problem("acquisition_label", gadget.acquisitionLabel(), "Unknown acquisition must not claim an event"));
      Integer cost = row.value().slotCost();
      if (cost != null && !GadgetPlate.canFit(List.of(cost)))
        errors.add(row.problem("slot_cost", cost, "Known gadget cost must fit a Gadget Plate row (1–3)"));
    }
    var versions = ids(data.versions(), GameVersion::id);
    var seen = new HashSet<UUID>();
    for (var snapshot : data.snapshots()) {
      if (!versions.contains(snapshot.versionId()) || !seen.add(snapshot.versionId()))
        errors.add("versions:1 field=game_version_id value=\"" + snapshot.versionId() + "\": Unknown or duplicate snapshot version");
      stats(snapshot.racers(), ids(data.racers(), Racer::id), "racer_id", errors);
      stats(snapshot.parts(), ids(data.parts(), MachinePart::id), "machine_part_id", errors);
    }
    for (var version : data.versions()) {
      if (!seen.contains(version.value().id())) errors.add(version.problem("id", version.value().id(), "Missing stat snapshot files"));
    }
    new GameDataRuleValidator().validate(data, errors);
    if (!errors.isEmpty()) throw new ImportValidationException(errors);
  }

  private void stats(List<CatalogRow<StatRow>> rows, Set<UUID> known, String field, List<String> errors) {
    unique(rows, StatRow::itemId, field, errors);
    for (var row : rows) if (!known.contains(row.value().itemId()))
      errors.add(row.problem(field, row.value().itemId(), "Stat row references an unknown catalog identity"));
  }

  private <T, K> void unique(List<CatalogRow<T>> rows, Function<T, K> key, String field, List<String> errors) {
    var seen = new HashSet<K>();
    for (var row : rows) if (!seen.add(key.apply(row.value())))
      errors.add(row.problem(field, key.apply(row.value()), "Duplicate value"));
  }

  private <T> Set<UUID> ids(List<CatalogRow<T>> rows, Function<T, UUID> id) {
    var result = new HashSet<UUID>();
    rows.forEach(r -> result.add(id.apply(r.value())));
    return result;
  }
}
