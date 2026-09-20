package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MachineCompositionMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void upgradesV26WithoutChangingCatalogStatsOrExistingBuilds() throws Exception {
    String schema = schema();
    try {
      migrate(schema, "26");
      String before;
      try (var connection = dataSource.getConnection()) {
        connection.setSchema(schema);
        try (var sql = connection.createStatement()) {
          sql.execute("INSERT INTO users(id,username,email,created_at) VALUES ('00000000-0000-4000-8000-000000000027','upgrade','upgrade@test.invalid',now())");
          sql.execute("INSERT INTO builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,created_at,updated_at) "
              + "SELECT m.id,m.name,'preserve','00000000-0000-4000-8000-000000000027',(SELECT id FROM racers LIMIT 1),f.id,r.id,t.id,now(),now() FROM machines m "
              + "JOIN machine_parts f ON f.source_machine_id=m.id AND f.part_type='FRONT' "
              + "JOIN machine_parts r ON r.source_machine_id=m.id AND r.part_type='REAR' "
              + "LEFT JOIN machine_parts t ON t.source_machine_id=m.id AND t.part_type='TIRE'");
          before = snapshot(sql);
        } finally {
          connection.setSchema("public");
        }
      }
      migrate(schema, "27");
      try (var connection = dataSource.getConnection()) {
        connection.setSchema(schema);
        try (var sql = connection.createStatement()) {
          assertEquals(before, snapshot(sql));
          assertEquals("0", value(sql, "SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='machines' AND column_name='family'"));
          assertEquals("YES", value(sql, "SELECT is_nullable FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='builds' AND column_name='tire_part_id'"));
        } finally {
          connection.setSchema("public");
        }
      }
    } finally { clean(schema); }
  }

  @Test
  void refusesContradictoryUnknownAndIncorrectSlotCatalogs() throws Exception {
    for (String corrupt : new String[] {
        "UPDATE machines SET racing_type='POWER' WHERE name='Diva Macchina'",
        "UPDATE machines SET racing_type='BOOST' WHERE name='Dark Reaper'",
        "UPDATE machines SET racing_type=NULL WHERE name='Dark Reaper'",
        "UPDATE machine_parts SET part_type='TIRE' WHERE part_type='REAR' AND source_machine_id=(SELECT id FROM machines WHERE name='Diva Macchina')"
    }) {
      String schema = schema();
      try {
        migrate(schema, "26");
        try (var connection = dataSource.getConnection()) {
          connection.setSchema(schema);
          try (var sql = connection.createStatement()) {
            sql.execute(corrupt);
          } finally {
            connection.setSchema("public");
          }
        }
        assertThrows(org.flywaydb.core.api.FlywayException.class, () -> migrate(schema, "27"));
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
          assertEquals("1", value(sql, "SELECT count(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='machines' AND column_name='family'"));
        }
      } finally { clean(schema); }
    }
  }

  private String snapshot(Statement sql) throws Exception {
    return value(sql, "SELECT jsonb_build_object('machines',(SELECT jsonb_agg(to_jsonb(m)-'family' ORDER BY id) FROM machines m),"
        + "'parts',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM machine_parts p),"
        + "'stats',(SELECT jsonb_agg(to_jsonb(s) ORDER BY machine_part_id,game_version_id) FROM machine_part_stats s),"
        + "'builds',(SELECT jsonb_agg(to_jsonb(b) ORDER BY id) FROM builds b))::text");
  }

  private String value(Statement sql, String query) throws Exception {
    try (var rows = sql.executeQuery(query)) { assertTrue(rows.next()); return rows.getString(1); }
  }
  private String schema() { return "composition_test_" + UUID.randomUUID().toString().replace("-", ""); }
  private void migrate(String schema, String version) {
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration").target(version).load().migrate();
  }
  private void clean(String schema) throws Exception {
    try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
      sql.execute("DROP SCHEMA " + schema + " CASCADE");
    }
  }
}
