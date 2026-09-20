package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BoardMachineMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void v25ClassifiesExistingMachinesAndPreservesBuildsWhileAllowingBoardTireToBeNull()
      throws Exception {
    String schema = "board_migration_" + UUID.randomUUID().toString().replace("-", "");
    try {
      migrate(schema, "24");
      int buildCount;
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        sql.execute("INSERT INTO users (id, username, email, created_at) VALUES "
            + "('00000000-0000-4000-8000-000000000025', 'board_test', 'board@test.invalid', now())");
        sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, front_part_id, "
            + "rear_part_id, tire_part_id, game_version_id, created_at, updated_at) "
            + "SELECT '00000000-0000-4000-8000-000000000025', 'Preserved', '', "
            + "'00000000-0000-4000-8000-000000000025', (SELECT id FROM racers LIMIT 1), "
            + "f.id, r.id, t.id, (SELECT id FROM game_versions ORDER BY released_at DESC LIMIT 1), now(), now() "
            + "FROM machines m "
            + "JOIN machine_parts f ON f.source_machine_id = m.id AND f.part_type = 'FRONT' "
            + "JOIN machine_parts r ON r.source_machine_id = m.id AND r.part_type = 'REAR' "
            + "JOIN machine_parts t ON t.source_machine_id = m.id AND t.part_type = 'TIRE' LIMIT 1");
        try (var rows = sql.executeQuery("SELECT count(*) FROM builds")) {
          assertTrue(rows.next());
          buildCount = rows.getInt(1);
        }
        connection.commit();
      }

      migrate(schema, "25");
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        try (var rows = sql.executeQuery(
            "SELECT count(*), count(*) FILTER (WHERE family = 'STANDARD') FROM machines")) {
          assertTrue(rows.next());
          assertEquals(rows.getInt(1), rows.getInt(2));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM builds")) {
          assertTrue(rows.next());
          assertEquals(buildCount, rows.getInt(1));
        }
        sql.execute("UPDATE machines SET family = 'BOARD' WHERE id = (SELECT source_machine_id "
            + "FROM machine_parts WHERE part_type = 'FRONT' LIMIT 1)");
        sql.execute("UPDATE builds SET tire_part_id = NULL "
            + "WHERE id = '00000000-0000-4000-8000-000000000025'");
        try (var rows = sql.executeQuery("SELECT count(*) FROM builds WHERE tire_part_id IS NULL")) {
          assertTrue(rows.next());
          assertEquals(1, rows.getInt(1));
        }
        connection.commit();
      }
    } finally {
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  private void migrate(String schema, String target) {
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration").target(target).load().migrate();
  }
}
