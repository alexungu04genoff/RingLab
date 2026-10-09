package dev.ringlab;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RecommendedMapsMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test void freshAndUpgradePreserveExistingDataAndEnforceAssociationConstraints() throws Exception {
    for (boolean upgrade : List.of(false, true)) {
      String schema = "maps_migration_" + UUID.randomUUID().toString().replace("-", "");
      try {
        var config = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration");
        if (upgrade) config.target("29").load().migrate();
        Map<String, String> before = Map.of();
        if (upgrade) {
          try (var c = dataSource.getConnection(); var sql = c.createStatement()) {
            c.setAutoCommit(false);
            sql.execute("SET LOCAL search_path TO " + schema);
            sql.execute("INSERT INTO users(id,username,email,password_hash,created_at) VALUES ('00000000-0000-4000-8000-000000000001','maptest','maptest@example.test','unused',now())");
            sql.execute("INSERT INTO builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,created_at,updated_at) SELECT '00000000-0000-4000-8000-000000000010','Existing','Notes','00000000-0000-4000-8000-000000000001',(SELECT id FROM racers LIMIT 1),f.id,r.id,t.id,'2026-01-01','2026-01-02' FROM machine_parts f JOIN machine_parts r ON f.source_machine_id=r.source_machine_id AND r.part_type='REAR' JOIN machine_parts t ON f.source_machine_id=t.source_machine_id AND t.part_type='TIRE' WHERE f.part_type='FRONT' LIMIT 1");
            sql.execute("INSERT INTO builds(id,title,description,author_id,racer_id,created_at,updated_at,front_part_id,rear_part_id,tire_part_id,game_version_id,remixed_from_build_id) SELECT '00000000-0000-4000-8000-000000000011',title,description,author_id,racer_id,created_at,updated_at,front_part_id,rear_part_id,tire_part_id,game_version_id,id FROM builds LIMIT 1");
            sql.execute("INSERT INTO build_gadgets SELECT '00000000-0000-4000-8000-000000000010',id,0 FROM gadgets LIMIT 1");
            sql.execute("INSERT INTO votes VALUES ('00000000-0000-4000-8000-000000000021','00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000010',1)");
            sql.execute("INSERT INTO comments VALUES ('00000000-0000-4000-8000-000000000022','00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000001','Keep me',now())");
            sql.execute("INSERT INTO saved_builds VALUES ('00000000-0000-4000-8000-000000000001','00000000-0000-4000-8000-000000000010',now())");
            before = snapshot(sql);
            c.commit();
          }
        }
        config.target("latest").load().migrate();
        try (var c = dataSource.getConnection(); var sql = c.createStatement()) {
          c.setAutoCommit(false);
          sql.execute("SET LOCAL search_path TO " + schema);
          assertEquals(0, count(sql, "build_recommended_maps"));
          assertTrue(count(sql, "race_maps") > 3);
          if (upgrade) {
            assertEquals(before, snapshot(sql));
            var rows = sql.executeQuery("SELECT id FROM race_maps ORDER BY catalog_order LIMIT 1");
            assertTrue(rows.next()); var map = rows.getString(1); rows.close();
            String association = "INSERT INTO build_recommended_maps VALUES ('00000000-0000-4000-8000-000000000010','" + map + "')";
            sql.execute(association);
            var duplicate = c.setSavepoint();
            assertThrows(SQLException.class, () -> sql.execute(association));
            c.rollback(duplicate);
            var deletion = c.setSavepoint();
            assertThrows(SQLException.class, () -> sql.execute("DELETE FROM race_maps WHERE id='" + map + "'"));
            c.rollback(deletion);
            sql.execute("DELETE FROM builds WHERE id='00000000-0000-4000-8000-000000000010'");
            assertEquals(0, count(sql, "build_recommended_maps"));
          }
          c.commit();
        }
      } finally {
        try (var c = dataSource.getConnection(); var sql = c.createStatement()) {
          sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
      }
    }
  }
  private Map<String, String> snapshot(Statement sql) throws SQLException {
    var result = new HashMap<String, String>();
    for (var table : List.of("users", "builds", "build_gadgets", "votes", "comments", "saved_builds")) {
      try (var rows = sql.executeQuery("SELECT coalesce(jsonb_agg(to_jsonb(t)-'visibility'-'first_published_at' ORDER BY to_jsonb(t)::text),'[]')::text FROM " + table + " t")) {
        rows.next(); result.put(table, rows.getString(1));
      }
    }
    return result;
  }
  private int count(Statement sql, String table) throws SQLException {
    try (var rows = sql.executeQuery("SELECT count(*) FROM " + table)) { rows.next(); return rows.getInt(1); }
  }
}
