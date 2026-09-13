package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BuildRemixMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void deletingSourceClearsProvenanceWithoutDeletingRemix() throws Exception {
    String schema = "remix_migration_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
          .locations("classpath:db/migration").load().migrate();
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + schema);
        sql.execute("INSERT INTO users VALUES ('00000000-0000-4000-8000-000000000001', "
            + "'remixer', 'remixer@example.test', 'unused', now())");
        sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, front_part_id, "
            + "rear_part_id, tire_part_id, created_at, updated_at) SELECT "
            + "'00000000-0000-4000-8000-000000000010', 'Source', '', "
            + "'00000000-0000-4000-8000-000000000001', (SELECT id FROM racers LIMIT 1), "
            + "f.id, r.id, t.id, now(), now() FROM machines m "
            + "JOIN machine_parts f ON f.source_machine_id=m.id AND f.part_type='FRONT' "
            + "JOIN machine_parts r ON r.source_machine_id=m.id AND r.part_type='REAR' "
            + "JOIN machine_parts t ON t.source_machine_id=m.id AND t.part_type='TIRE' LIMIT 1");
        sql.execute("INSERT INTO builds (id, title, description, author_id, racer_id, front_part_id, "
            + "rear_part_id, tire_part_id, remixed_from_build_id, created_at, updated_at) SELECT "
            + "'00000000-0000-4000-8000-000000000011', 'Remix', description, author_id, racer_id, "
            + "front_part_id, rear_part_id, tire_part_id, id, now(), now() FROM builds "
            + "WHERE id='00000000-0000-4000-8000-000000000010'");

        sql.execute("DELETE FROM builds WHERE id='00000000-0000-4000-8000-000000000010'");

        try (var rows = sql.executeQuery("SELECT remixed_from_build_id FROM builds WHERE "
            + "id='00000000-0000-4000-8000-000000000011'")) {
          assertTrue(rows.next());
          assertNull(rows.getObject(1));
          assertFalse(rows.next());
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
