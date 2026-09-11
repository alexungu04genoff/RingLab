package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GameVersionMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void v5PreservesVersionlessV4BuildsAndTheirRelatedData() throws Exception {
    String schema = "version_migration_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").target("4").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        sql.execute("INSERT INTO users VALUES ('00000000-0000-4000-8000-000000000001', 'test', 'test@example.test', 'unused', now())");
        sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, front_part_id, rear_part_id, tire_part_id, created_at, updated_at) "
            + "SELECT m.id, m.name, 'preserved', '00000000-0000-4000-8000-000000000001', "
            + "(SELECT id FROM racers LIMIT 1), f.id, r.id, t.id, '2026-01-01', '2026-01-02' FROM machines m "
            + "JOIN machine_parts f ON f.source_machine_id = m.id AND f.part_type = 'FRONT' "
            + "JOIN machine_parts r ON r.source_machine_id = m.id AND r.part_type = 'REAR' "
            + "JOIN machine_parts t ON t.source_machine_id = m.id AND t.part_type = 'TIRE'");
        sql.execute("INSERT INTO build_gadgets SELECT id, '238359e7-ba2a-582d-bc82-6ab1c14b0284', 0 FROM builds");
        sql.execute("INSERT INTO build_gadgets SELECT id, '174ea0a3-43bb-5001-bbd4-8b598482fe59', 1 FROM builds");
        sql.execute("INSERT INTO votes SELECT id, author_id, id, 1 FROM builds");
        sql.execute("INSERT INTO comments SELECT id, id, author_id, 'preserved comment', created_at FROM builds");
        for (String table : java.util.List.of("builds", "build_gadgets", "votes", "comments")) {
          sql.execute("CREATE TABLE before_" + table + " AS SELECT * FROM " + table);
        }
        connection.commit();
      }
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").target("5").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        try (var rows = sql.executeQuery("SELECT count(*), count(game_version_id) FROM builds")) {
          assertTrue(rows.next());
          assertEquals(10, rows.getInt(1));
          assertEquals(0, rows.getInt(2));
        }
        for (String table : java.util.List.of("builds", "build_gadgets", "votes", "comments")) {
          String projection = table.equals("builds") ? "to_jsonb(current_row) - 'game_version_id'" : "to_jsonb(current_row)";
          try (var rows = sql.executeQuery("SELECT count(*) FROM (SELECT " + projection
              + " FROM " + table + " current_row EXCEPT SELECT to_jsonb(old_row) FROM before_" + table + " old_row) differences")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1), table);
          }
        }
        connection.rollback();
      }
    } finally {
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }
}
