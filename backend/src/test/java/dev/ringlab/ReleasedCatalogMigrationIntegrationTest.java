package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Manual PostgreSQL boundary check: never run against a production database. */
@QuarkusTest
class ReleasedCatalogMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void upgradesExistingCatalogAndPreservesReferencedParts() throws Exception {
    String schema = "catalog_test_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").target("5").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        sql.execute("CREATE TABLE previous_parts AS SELECT * FROM machine_parts");
        sql.execute("CREATE TABLE previous_racers AS SELECT id, name, racing_type FROM racers");
        sql.execute("INSERT INTO users VALUES ('00000000-0000-4000-8000-000000000001', 'test', 'test@example.test', 'unused', now())");
        sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, front_part_id, rear_part_id, tire_part_id, created_at, updated_at) "
            + "VALUES ('00000000-0000-4000-8000-000000000002', 'Keep me', 'Existing build', "
            + "'00000000-0000-4000-8000-000000000001', '013ecae2-55b9-58bb-b1c5-b054578058ff', "
            + "'10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002', "
            + "'30000000-0000-4000-8000-000000000003', now(), now())");
        connection.commit();
      }
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").target("6").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        try (var rows = sql.executeQuery("SELECT (SELECT count(*) FROM racers), "
            + "(SELECT count(*) FROM machines), (SELECT count(*) FROM machine_parts)")) {
          assertTrue(rows.next());
          assertEquals(53, rows.getInt(1));
          assertEquals(27, rows.getInt(2));
          assertEquals(81, rows.getInt(3));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM (SELECT m.id FROM machines m "
            + "LEFT JOIN machine_parts p ON p.source_machine_id = m.id GROUP BY m.id "
            + "HAVING count(*) <> 3 OR count(DISTINCT p.part_type) <> 3) invalid")) {
          assertTrue(rows.next());
          assertEquals(0, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM previous_parts old "
            + "JOIN machine_parts p USING (id, source_machine_id, part_type)")) {
          assertTrue(rows.next());
          assertEquals(30, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM previous_racers old "
            + "JOIN racers r USING (id, name, racing_type)")) {
          assertTrue(rows.next());
          assertEquals(6, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT front_part_id, rear_part_id, tire_part_id FROM builds WHERE title = 'Keep me'")) {
          assertTrue(rows.next());
          assertEquals("10000000-0000-4000-8000-000000000001", rows.getString(1));
          assertEquals("20000000-0000-4000-8000-000000000002", rows.getString(2));
          assertEquals("30000000-0000-4000-8000-000000000003", rows.getString(3));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM racers WHERE name IN "
            + "('Inky', 'Pinky', 'Clyde', 'Amigo (Classic)', 'Aang', 'Katara', 'GHOSTS', 'Team Ghost')")) {
          assertTrue(rows.next());
          assertEquals(0, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM racers WHERE name IN ('PAC-MAN', 'Blinky', 'Amigo', 'Axel', 'Classic Sonic')")) {
          assertTrue(rows.next());
          assertEquals(5, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT count(*) FROM machines WHERE name = 'Gliding Air'")) {
          assertTrue(rows.next());
          assertEquals(0, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT (SELECT count(*) FROM racers WHERE racing_type IS NULL), "
            + "(SELECT count(*) FROM machines WHERE racing_type IS NULL)")) {
          assertTrue(rows.next());
          assertEquals(47, rows.getInt(1));
          assertEquals(17, rows.getInt(2));
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
