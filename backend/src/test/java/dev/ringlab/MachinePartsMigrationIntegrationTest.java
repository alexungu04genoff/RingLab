package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Runs V1–V3 and V4 in an isolated schema within the disposable test database. */
@QuarkusTest
class MachinePartsMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void migratesExistingBuildsWithoutLosingTheirConfigurationOrSocialData() throws Exception {
    String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").target("3").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        sql.execute("INSERT INTO users VALUES ('00000000-0000-4000-8000-000000000001', 'test', 'test@example.test', 'unused', now())");
        sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, machine_id, created_at, updated_at) "
            + "SELECT m.id, m.name, 'preserved', '00000000-0000-4000-8000-000000000001', "
            + "'013ecae2-55b9-58bb-b1c5-b054578058ff', m.id, now(), now() FROM machines m");
        sql.execute("INSERT INTO build_gadgets SELECT id, '238359e7-ba2a-582d-bc82-6ab1c14b0284', 0 FROM builds");
        sql.execute("INSERT INTO build_gadgets SELECT id, '174ea0a3-43bb-5001-bbd4-8b598482fe59', 1 FROM builds");
        sql.execute("INSERT INTO votes SELECT id, author_id, id, 1 FROM builds");
        sql.execute("INSERT INTO comments SELECT id, id, author_id, 'preserved comment', created_at FROM builds");
        connection.commit();
      }
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        try (var rows = sql.executeQuery("SELECT count(*) FROM builds b "
            + "JOIN machine_parts f ON f.id = b.front_part_id AND f.part_type = 'FRONT' AND f.source_machine_id = b.id "
            + "JOIN machine_parts r ON r.id = b.rear_part_id AND r.part_type = 'REAR' AND r.source_machine_id = b.id "
            + "JOIN machine_parts t ON t.id = b.tire_part_id AND t.part_type = 'TIRE' AND t.source_machine_id = b.id "
            + "WHERE b.description = 'preserved'")) {
          assertTrue(rows.next());
          assertEquals(10, rows.getInt(1));
        }
        try (var rows = sql.executeQuery("SELECT (SELECT count(*) FROM votes WHERE value = 1), "
            + "(SELECT count(*) FROM comments WHERE text = 'preserved comment'), "
            + "(SELECT count(*) FROM build_gadgets WHERE (position = 0 AND gadget_id = '238359e7-ba2a-582d-bc82-6ab1c14b0284') "
            + "OR (position = 1 AND gadget_id = '174ea0a3-43bb-5001-bbd4-8b598482fe59'))")) {
          assertTrue(rows.next());
          assertEquals(10, rows.getInt(1));
          assertEquals(10, rows.getInt(2));
          assertEquals(20, rows.getInt(3));
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
