package dev.ringlab.application.gamedata;

import dev.ringlab.adapter.out.db.gamedata.GameDataImportDbAdapter;
import dev.ringlab.adapter.out.registry.PublicationContract;
import dev.ringlab.domain.gamedata.registry.VerifiedRegistryPublication;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RegistryComparisonIntegrationTest {
  @Inject DataSource dataSource;

  @Test void comparisonReadsARepeatableReadSnapshotRollsBackAndNeverIssuesAWritingStatement() throws Exception {
    String schema = "registry_compare_test_" + UUID.randomUUID().toString().replace("-", "");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
      var isolated = new PGSimpleDataSource();
      try (var c = dataSource.getConnection()) { isolated.setURL(c.getMetaData().getURL()); }
      var config = ConfigProvider.getConfig();
      isolated.setUser(config.getValue("quarkus.datasource.username", String.class));
      isolated.setPassword(config.getValue("quarkus.datasource.password", String.class)); isolated.setCurrentSchema(schema);
      var repository = new GameDataImportDbAdapter(isolated);
      var before = repository.read();
      var queries = new AtomicInteger(); var rollbacks = new AtomicInteger();
      DataSource guarded = (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
        Object result = invoke(method, isolated, args);
        if (!(result instanceof Connection connection)) return result;
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
          assertNotEquals("commit", m.getName(), "Comparison must never commit");
          if (m.getName().equals("rollback")) rollbacks.incrementAndGet();
          Object value = invoke(m, connection, a);
          if (!(value instanceof Statement statement)) return value;
          assertTrue(connection.isReadOnly()); assertFalse(connection.getAutoCommit());
          assertEquals(Connection.TRANSACTION_REPEATABLE_READ, connection.getTransactionIsolation());
          String prepared = a != null && a.length > 0 && a[0] instanceof String sql ? sql : null;
          Class<?> type = statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
          return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (sp, sm, sa) -> {
            if (sm.getName().startsWith("execute") || sm.getName().equals("addBatch")) {
              assertEquals("executeQuery", sm.getName(), "Only SELECT queries are permitted");
              String sql = prepared != null ? prepared : (String)sa[0];
              assertTrue(sql.startsWith("SELECT "), sql); queries.incrementAndGet();
            }
            return invoke(sm, statement, sa);
          });
        });
      });
      var contract = new PublicationContract();
      var publication = contract.read(Files.readAllBytes(Path.of("src/test/resources/registry/baseline-v1.json")));
      var result = new RegistryComparisonService(() -> Optional.of(new VerifiedRegistryPublication(publication, contract.metadata(publication).datasetFingerprint())),
          new GameDataImportDbAdapter(guarded)).compareLatest().orElseThrow();
      assertTrue(result.projectionCompatible(), result.protectedChanges().toString());
      assertEquals(117, result.proposedPresentationUpdates(), "Migrations alone do not include later CSV artwork/acquisition updates");
      assertEquals(0, result.proposedAdditions()); assertEquals(1, rollbacks.get()); assertTrue(queries.get() >= 13);
      assertEquals(before, repository.read(), "All catalog, stat and rule rows must remain unchanged");
      assertEquals(CatalogFingerprint.snapshot(before), result.databaseFingerprint());
      // PostgreSQL itself enforces the safety boundary, not only the Java observer.
      try (var c = isolated.getConnection()) {
        c.setReadOnly(true); c.setAutoCommit(false);
        var exception = assertThrows(SQLException.class, () -> { try (var s = c.createStatement()) { s.executeUpdate("UPDATE racers SET name=name"); } });
        assertEquals("25006", exception.getSQLState()); c.rollback();
      }
    } finally {
      try (var c = dataSource.getConnection(); var s = c.createStatement()) { s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }
  }
  private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
    try { return method.invoke(target, args); } catch (InvocationTargetException e) { throw e.getCause(); }
  }
}
