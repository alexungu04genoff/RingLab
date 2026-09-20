package dev.ringlab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BoardMachineMigrationIntegrationTest {
  private static final String BOARD_BUILD = "00000000-0000-4000-8000-000000000026";
  private static final String STANDARD_BUILD = "00000000-0000-4000-8000-000000000027";

  @Inject DataSource dataSource;

  @Test
  void v26ClassifiesBoardsRemovesOnlyBoardTiresAndPreservesBuildsAndValidParts()
      throws Exception {
    String schema = "board_migration_" + UUID.randomUUID().toString().replace("-", "");
    UUID divaFront;
    UUID divaRear;
    UUID standardTire;
    try {
      migrate(schema, "24");
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        divaFront = partId(sql, "Diva Macchina", "FRONT");
        divaRear = partId(sql, "Diva Macchina", "REAR");
        standardTire = partId(sql, "Dark Reaper", "TIRE");
        sql.execute("INSERT INTO users (id, username, email, created_at) VALUES "
            + "('00000000-0000-4000-8000-000000000026', 'board_test', "
            + "'board@test.invalid', now())");
        insertStockBuild(sql, BOARD_BUILD, "Board build", "Diva Macchina");
        insertStockBuild(sql, STANDARD_BUILD, "Standard build", "Dark Reaper");
        connection.commit();
      }

      migrate(schema, "26");
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        assertEquals("BOARD", value(sql,
            "SELECT family FROM machines WHERE name = 'Diva Macchina'"));
        assertEquals("STANDARD", value(sql,
            "SELECT family FROM machines WHERE name = 'Dark Reaper'"));
        assertEquals(12, count(sql, "SELECT count(*) FROM machines WHERE family = 'BOARD'"));
        assertEquals(24, count(sql, "SELECT count(*) FROM machine_parts part "
            + "JOIN machines machine ON machine.id = part.source_machine_id "
            + "WHERE machine.family = 'BOARD'"));
        assertEquals(0, count(sql, "SELECT count(*) FROM machine_parts part "
            + "JOIN machines machine ON machine.id = part.source_machine_id "
            + "WHERE machine.family = 'BOARD' AND part.part_type = 'TIRE'"));
        assertEquals(3, count(sql, "SELECT count(*) FROM machine_parts part "
            + "JOIN machines machine ON machine.id = part.source_machine_id "
            + "WHERE machine.name = 'Dark Reaper'"));
        assertEquals(divaFront, partId(sql, "Diva Macchina", "FRONT"));
        assertEquals(divaRear, partId(sql, "Diva Macchina", "REAR"));
        assertEquals(2, count(sql, "SELECT count(*) FROM builds WHERE id IN ('"
            + BOARD_BUILD + "', '" + STANDARD_BUILD + "')"));
        assertEquals(1, count(sql, "SELECT count(*) FROM builds WHERE id = '"
            + BOARD_BUILD + "' AND tire_part_id IS NULL"));
        assertEquals(standardTire, UUID.fromString(value(sql,
            "SELECT tire_part_id::text FROM builds WHERE id = '" + STANDARD_BUILD + "'")));
        assertEquals(0, count(sql, "SELECT count(*) FROM machine_part_stats stats "
            + "LEFT JOIN machine_parts part ON part.id = stats.machine_part_id "
            + "WHERE part.id IS NULL"));
        connection.commit();
      }
    } finally {
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  private void insertStockBuild(java.sql.Statement sql, String id, String title, String machine)
      throws Exception {
    sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, front_part_id, "
        + "rear_part_id, tire_part_id, game_version_id, created_at, updated_at) "
        + "SELECT '" + id + "', '" + title + "', '', "
        + "'00000000-0000-4000-8000-000000000026', (SELECT id FROM racers LIMIT 1), "
        + "front.id, rear.id, tire.id, "
        + "(SELECT id FROM game_versions ORDER BY released_at DESC LIMIT 1), now(), now() "
        + "FROM machines machine "
        + "JOIN machine_parts front ON front.source_machine_id = machine.id "
        + "AND front.part_type = 'FRONT' "
        + "JOIN machine_parts rear ON rear.source_machine_id = machine.id "
        + "AND rear.part_type = 'REAR' "
        + "JOIN machine_parts tire ON tire.source_machine_id = machine.id "
        + "AND tire.part_type = 'TIRE' WHERE machine.name = '" + machine + "'");
  }

  private UUID partId(java.sql.Statement sql, String machine, String type) throws Exception {
    return UUID.fromString(value(sql, "SELECT part.id::text FROM machine_parts part "
        + "JOIN machines machine ON machine.id = part.source_machine_id "
        + "WHERE machine.name = '" + machine + "' AND part.part_type = '" + type + "'"));
  }

  private String value(java.sql.Statement sql, String query) throws Exception {
    try (var rows = sql.executeQuery(query)) {
      assertTrue(rows.next());
      String value = rows.getString(1);
      assertFalse(rows.next());
      return value;
    }
  }

  private int count(java.sql.Statement sql, String query) throws Exception {
    return Integer.parseInt(value(sql, query));
  }

  private void migrate(String schema, String target) {
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration").target(target).load().migrate();
  }
}
