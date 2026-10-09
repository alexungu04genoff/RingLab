package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import dev.ringlab.port.out.GameDataImportRepository;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;

/** A short-lived JDBC command session, independent of Quarkus/Flyway startup. */
public final class GameDataImportDbAdapter implements GameDataImportRepository {
  private final DataSource dataSource;

  public GameDataImportDbAdapter(DataSource dataSource) { this.dataSource = dataSource; }

  @Override public GameDataSet read() {
    try (var connection = dataSource.getConnection()) {
      connection.setReadOnly(true);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setAutoCommit(false);
      var result = load(connection);
      connection.rollback();
      return result;
    } catch (SQLException e) { throw databaseFailure(e); }
  }

  @Override public GameDataImportPlan apply(Function<GameDataSet, GameDataImportPlan> prepare) {
    try (var connection = dataSource.getConnection()) {
      connection.setReadOnly(false);
      connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      connection.setAutoCommit(false);
      try {
        try (var statement = connection.createStatement()) {
          statement.execute("SET LOCAL lock_timeout = '10s'");
          // Serializes this importer only. Normal build/catalog reads are not locked.
          statement.execute("SELECT pg_advisory_xact_lock(1380731202)");
        }
        var plan = prepare.apply(load(connection));
        if (!plan.safe()) throw new IllegalArgumentException("Unsafe import plan");
        insert(connection, plan.additions());
        update(connection, plan.updates());
        connection.commit();
        return plan;
      } catch (SQLException | RuntimeException e) {
        connection.rollback();
        throw e;
      }
    } catch (SQLException e) { throw databaseFailure(e); }
  }

  private IllegalStateException databaseFailure(SQLException e) {
    // JDBC connection messages can contain URLs or credentials. Keep console diagnostics safe.
    return new IllegalStateException("Database operation failed (SQLSTATE " + e.getSQLState()
        + "). Check database access/schema/constraints and run plan again before retrying; a lost COMMIT response can leave the outcome uncertain.");
  }

  private GameDataSet load(Connection c) throws SQLException {
    var racers = query(c, "racers", "SELECT id,name,racing_type,image_path FROM racers ORDER BY id",
        r -> new Racer(uuid(r, "id"), r.getString("name"), racingType(r), r.getString("image_path")));
    var machines = query(c, "machines", "SELECT id,name,racing_type,image_path FROM machines ORDER BY id",
        r -> new Machine(uuid(r, "id"), r.getString("name"), racingType(r), r.getString("image_path")));
    var parts = query(c, "machine_parts", "SELECT id,source_machine_id,part_type FROM machine_parts ORDER BY id",
        r -> new MachinePart(uuid(r, "id"), uuid(r, "source_machine_id"), MachinePartType.valueOf(r.getString("part_type"))));
    var gadgets = query(c, "gadgets", "SELECT id,name,description,slot_cost,image_path,acquisition_kind,acquisition_label FROM gadgets ORDER BY id",
        r -> new Gadget(uuid(r, "id"), r.getString("name"), r.getString("description"),
            r.getObject("slot_cost", Integer.class), r.getString("image_path"),
            GadgetAcquisitionKind.valueOf(r.getString("acquisition_kind")), r.getString("acquisition_label")));
    var maps = query(c, "race_maps", "SELECT id,name,category,content_pack,image_path,catalog_order FROM race_maps ORDER BY id",
        r -> new RaceMap(uuid(r, "id"), r.getString("name"), RaceMap.Category.valueOf(r.getString("category")),
            r.getString("content_pack"), r.getString("image_path"), r.getInt("catalog_order")));
    var versions = query(c, "game_versions", "SELECT id,version,released_at FROM game_versions ORDER BY id",
        r -> new GameVersion(uuid(r, "id"), r.getString("version"), r.getObject("released_at", LocalDate.class)));
    var racerStats = readStats(c, "racer_stats", "racer_id");
    var partStats = readStats(c, "machine_part_stats", "machine_part_id");
    var snapshots = versions.stream().map(v -> new VersionStats(v.value().id(),
        racerStats.getOrDefault(v.value().id(), List.of()), partStats.getOrDefault(v.value().id(), List.of()))).toList();
    return new GameDataSet(racers, machines, parts, gadgets, maps, versions, snapshots, new GameDataRuleStorage().read(c, null));
  }

  private Map<UUID, List<CatalogRow<StatRow>>> readStats(Connection c, String table, String id) throws SQLException {
    var result = new LinkedHashMap<UUID, List<CatalogRow<StatRow>>>();
    // Table and column names are internal constants, never file values.
    try (var statement = c.createStatement(); var r = statement.executeQuery("SELECT " + id
        + ",game_version_id,speed,acceleration,handling,power,boost FROM " + table + " ORDER BY game_version_id," + id)) {
      while (r.next()) {
        var stats = new BaseStats(r.getBigDecimal("speed"), r.getBigDecimal("acceleration"),
            r.getBigDecimal("handling"), r.getBigDecimal("power"), r.getBigDecimal("boost"));
        result.computeIfAbsent(uuid(r, "game_version_id"), ignored -> new ArrayList<>())
            .add(new CatalogRow<>("database/" + table, r.getRow(), new StatRow(uuid(r, id), stats)));
      }
    }
    return result;
  }

  private void insert(Connection c, GameDataSet d) throws SQLException {
    for (var row : d.racers()) {
      var r = row.value();
      execute(c, "INSERT INTO racers(id,name,racing_type,image_path) VALUES (?,?,?,?)", r.id(), r.name(), r.racingType(), r.imagePath());
    }
    for (var row : d.machines()) {
      var m = row.value();
      execute(c, "INSERT INTO machines(id,name,racing_type,image_path) VALUES (?,?,?,?)", m.id(), m.name(), m.racingType(), m.imagePath());
    }
    for (var row : d.parts()) {
      var p = row.value();
      execute(c, "INSERT INTO machine_parts(id,source_machine_id,part_type) VALUES (?,?,?)", p.id(), p.sourceMachineId(), p.type());
    }
    for (var row : d.gadgets()) {
      var g = row.value();
      execute(c, "INSERT INTO gadgets(id,name,description,slot_cost,image_path,acquisition_kind,acquisition_label) VALUES (?,?,?,?,?,?,?)",
          g.id(), g.name(), g.description(), g.slotCost(), g.imagePath(), g.acquisitionKind(), g.acquisitionLabel());
    }
    for (var row : d.maps()) {
      var m = row.value();
      execute(c, "INSERT INTO race_maps(id,name,category,content_pack,image_path,catalog_order) VALUES (?,?,?,?,?,?)",
          m.id(), m.name(), m.category(), m.contentPack(), m.imagePath(), m.catalogOrder());
    }
    for (var row : d.versions()) {
      var v = row.value();
      execute(c, "INSERT INTO game_versions(id,version,released_at) VALUES (?,?,?)", v.id(), v.version(), v.releasedAt());
    }
    for (var snapshot : d.snapshots()) {
      insertStats(c, "racer_stats", "racer_id", snapshot.versionId(), snapshot.racers());
      insertStats(c, "machine_part_stats", "machine_part_id", snapshot.versionId(), snapshot.parts());
    }
    new GameDataRuleStorage().insert(c, d.ruleSets());
  }

  private void insertStats(Connection c, String table, String column, UUID version, List<CatalogRow<StatRow>> rows) throws SQLException {
    for (var row : rows) {
      var s = row.value().stats();
      execute(c, "INSERT INTO " + table + "(" + column + ",game_version_id,speed,acceleration,handling,power,boost) VALUES (?,?,?,?,?,?,?)",
          row.value().itemId(), version, s.speed(), s.acceleration(), s.handling(), s.power(), s.boost());
    }
  }

  private void update(Connection c, GameDataSet d) throws SQLException {
    for (var row : d.racers()) {
      var r = row.value();
      execute(c, "UPDATE racers SET name=?,image_path=? WHERE id=?", r.name(), r.imagePath(), r.id());
    }
    for (var row : d.machines()) {
      var m = row.value();
      execute(c, "UPDATE machines SET name=?,image_path=? WHERE id=?", m.name(), m.imagePath(), m.id());
    }
    for (var row : d.gadgets()) {
      var g = row.value();
      execute(c, "UPDATE gadgets SET name=?,description=?,image_path=?,acquisition_kind=?,acquisition_label=? WHERE id=?",
          g.name(), g.description(), g.imagePath(), g.acquisitionKind(), g.acquisitionLabel(), g.id());
    }
    for (var row : d.maps()) {
      var m = row.value();
      execute(c, "UPDATE race_maps SET name=?,content_pack=?,image_path=? WHERE id=?", m.name(), m.contentPack(), m.imagePath(), m.id());
    }
  }

  private void execute(Connection c, String sql, Object... values) throws SQLException {
    try (var statement = c.prepareStatement(sql)) {
      for (int i = 0; i < values.length; i++)
        statement.setObject(i + 1, values[i] instanceof Enum<?> e ? e.name() : values[i]);
      if (statement.executeUpdate() != 1) throw new SQLException("Unexpected affected row count", "P0001");
    }
  }

  private <T> List<CatalogRow<T>> query(Connection c, String table, String sql, RowReader<T> read) throws SQLException {
    var result = new ArrayList<CatalogRow<T>>();
    try (var statement = c.createStatement(); var rows = statement.executeQuery(sql)) {
      while (rows.next()) result.add(new CatalogRow<>("database/" + table, rows.getRow(), read.read(rows)));
    }
    return result;
  }

  private static UUID uuid(ResultSet r, String field) throws SQLException { return r.getObject(field, UUID.class); }
  private static RacingType racingType(ResultSet r) throws SQLException {
    String value = r.getString("racing_type");
    return value == null ? null : RacingType.valueOf(value);
  }
  @FunctionalInterface private interface RowReader<T> { T read(ResultSet rows) throws SQLException; }
}
