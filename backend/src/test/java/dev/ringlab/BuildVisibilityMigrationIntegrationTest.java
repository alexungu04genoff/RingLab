package dev.ringlab;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class BuildVisibilityMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test void freshAndVersion36UpgradePreserveContentAndBackfillPublication() throws Exception {
    for (boolean upgrade : List.of(false, true)) {
      String schema = "visibility_" + UUID.randomUUID().toString().replace("-", "");
      try {
        var config = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration");
        if (upgrade) {
          config.target("36").load().migrate();
          try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.execute("SET LOCAL search_path TO " + schema);
            sql.execute("INSERT INTO users(id,username,email,password_hash,created_at) VALUES ('00000000-0000-4000-8000-000000000001','publication','publication@example.test','unused',now())");
            sql.execute("INSERT INTO builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,created_at,updated_at) SELECT '00000000-0000-4000-8000-000000000002','Existing','Keep notes','00000000-0000-4000-8000-000000000001',(SELECT id FROM racers LIMIT 1),f.id,r.id,t.id,'2026-01-01','2026-02-01' FROM machine_parts f JOIN machine_parts r ON f.source_machine_id=r.source_machine_id AND r.part_type='REAR' JOIN machine_parts t ON f.source_machine_id=t.source_machine_id AND t.part_type='TIRE' WHERE f.part_type='FRONT' LIMIT 1");
            connection.commit();
          }
        }
        config.target("latest").load().migrate();
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
          connection.setAutoCommit(false);
          sql.execute("SET LOCAL search_path TO " + schema);
          try (var columns = sql.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='builds' AND column_name IN ('visibility','first_published_at')")) {
            assertTrue(columns.next()); assertEquals(2, columns.getInt(1));
          }
          if (upgrade) {
            try (var row = sql.executeQuery("SELECT title, description, visibility, created_at=first_published_at, updated_at>created_at FROM builds")) {
              assertTrue(row.next()); assertEquals("Existing", row.getString(1)); assertEquals("Keep notes", row.getString(2));
              assertEquals("PUBLIC", row.getString(3)); assertTrue(row.getBoolean(4)); assertTrue(row.getBoolean(5));
            }
            for (String invalid : List.of("NULL", "'UNLISTED'")) {
              var point = connection.setSavepoint();
              assertThrows(SQLException.class, () -> sql.execute("UPDATE builds SET visibility=" + invalid));
              connection.rollback(point);
            }
            sql.execute("UPDATE builds SET visibility='PRIVATE',first_published_at=NULL");
          }
          connection.commit();
        }
      } finally {
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
          sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
      }
    }
  }
}
