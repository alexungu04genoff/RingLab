package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GadgetCatalogMigrationIntegrationTest {
  @Inject DataSource dataSource;

  @Test
  void v9PreservesEveryReferenceAndCommunityRowAndMatchesAFreshInstall() throws Exception {
    String upgraded = "gadget_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    String fresh = "gadget_fresh_" + UUID.randomUUID().toString().replace("-", "");
    List<String> untouched = List.of("users", "builds", "build_gadgets", "votes", "comments",
        "machine_parts", "game_versions");
    try {
      migrate(upgraded, "6");
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + upgraded);
        sql.execute("INSERT INTO users VALUES ('00000000-0000-4000-8000-000000000001', 'test', 'test@example.test', 'unused', now())");
        sql.execute("INSERT INTO builds (id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,game_version_id,created_at,updated_at) "
            + "VALUES ('00000000-0000-4000-8000-000000000002','Preserved','Existing build',"
            + "'00000000-0000-4000-8000-000000000001','013ecae2-55b9-58bb-b1c5-b054578058ff',"
            + "'10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000002',"
            + "'30000000-0000-4000-8000-000000000003','50000000-0000-0000-0000-000000000001','2026-01-01','2026-01-02')");
        sql.execute("INSERT INTO build_gadgets SELECT '00000000-0000-4000-8000-000000000002', id, "
            + "row_number() OVER (ORDER BY name DESC) - 1 FROM gadgets");
        sql.execute("INSERT INTO votes SELECT id,author_id,id,1 FROM builds");
        sql.execute("INSERT INTO comments SELECT id,id,author_id,'Keep this comment',created_at FROM builds");
        sql.execute("CREATE TABLE before_gadgets AS SELECT * FROM gadgets");
        sql.execute("CREATE TABLE before_machines AS SELECT * FROM machines");
        sql.execute("CREATE TABLE before_racers AS SELECT * FROM racers");
        for (String table : untouched) sql.execute("CREATE TABLE before_" + table + " AS SELECT * FROM " + table);
        connection.commit();
      }
      migrate(upgraded, "9");
      migrate(fresh, "9");
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        connection.setAutoCommit(false);
        sql.execute("SET LOCAL search_path TO " + upgraded);
        for (String table : untouched) assertSameRows(sql, table, "before_" + table);
        assertSameRows(sql, "(SELECT id, name, racing_type FROM machines) current_machines",
            "(SELECT id, name, racing_type FROM before_machines) old_machines");
        assertSameRows(sql, "(SELECT id, name, racing_type FROM racers) current_racers",
            "(SELECT id, name, racing_type FROM before_racers) old_racers");
        assertSameRows(sql, "gadgets", fresh + ".gadgets");
        assertZero(sql, "SELECT count(*) FROM before_gadgets old LEFT JOIN gadgets g USING(id) WHERE g.id IS NULL");
        assertZero(sql, "SELECT count(*) FROM build_gadgets bg LEFT JOIN gadgets g ON g.id=bg.gadget_id WHERE g.id IS NULL");
        assertZero(sql, "SELECT count(*) FROM (SELECT name FROM gadgets GROUP BY name HAVING count(*)>1) duplicates");
        assertZero(sql, "SELECT count(*) FROM gadgets WHERE slot_cost IS NOT NULL AND slot_cost NOT BETWEEN 1 AND 3");
        connection.rollback();
      }
    } finally {
      try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
        sql.execute("DROP SCHEMA IF EXISTS " + upgraded + " CASCADE");
        sql.execute("DROP SCHEMA IF EXISTS " + fresh + " CASCADE");
      }
    }
  }

  private void migrate(String schema, String version) {
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration").target(version).load().migrate();
  }

  private void assertSameRows(Statement sql, String first, String second) throws Exception {
    // Both directions detect inserted/deleted/changed values; EXCEPT ALL also preserves multiplicity.
    assertZero(sql, "SELECT count(*) FROM ((SELECT * FROM " + first + " EXCEPT ALL SELECT * FROM " + second
        + ") UNION ALL (SELECT * FROM " + second + " EXCEPT ALL SELECT * FROM " + first + ")) differences");
  }

  private void assertZero(Statement sql, String query) throws Exception {
    try (var rows = sql.executeQuery(query)) {
      assertTrue(rows.next());
      assertEquals(0, rows.getInt(1), query);
    }
  }
}
