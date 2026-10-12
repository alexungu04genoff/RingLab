package dev.ringlab;

import dev.ringlab.adapter.in.catalog.RegistryComparisonCommand;
import dev.ringlab.adapter.out.db.gamedata.GameDataImportDbAdapter;
import dev.ringlab.adapter.out.registry.RegistryHttpAdapter;
import dev.ringlab.application.gamedata.RegistryComparisonService;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.postgresql.ds.PGSimpleDataSource;

/** One-shot composition root. Does not start Quarkus, Flyway, servers or background polling. */
public final class RegistryComparisonMain {
  private RegistryComparisonMain() {}
  public static void main(String[] args) {
    // Standalone execution still uses the logging provider shipped in Quarkus's boot libraries.
    System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    System.exit(run(args, System.getenv()));
  }
  static int run(String[] args, Map<String,String> environment) {
    if (args.length != 2 || !args[0].equals("compare")) {
      System.err.println("Usage: RegistryComparisonMain compare <new-report.json>"); return 2;
    }
    try {
      String registry = required(environment, "REGISTRY_URL");
      var source = new PGSimpleDataSource();
      String database = required(environment, "DB_URL");
      if (!database.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException();
      source.setURL(database); source.setUser(required(environment, "DB_USER")); source.setPassword(required(environment, "DB_PASSWORD"));
      source.setConnectTimeout(10); source.setSocketTimeout(30);
      long seconds = Long.parseLong(environment.getOrDefault("REGISTRY_TIMEOUT_SECONDS", "15"));
      if (seconds < 1 || seconds > 120) throw new IllegalArgumentException();
      var remote = new RegistryHttpAdapter(URI.create(registry), Duration.ofSeconds(seconds),
          Integer.parseInt(environment.getOrDefault("REGISTRY_MAX_BYTES", "16777216")));
      var service = new RegistryComparisonService(remote, new GameDataImportDbAdapter(source));
      return new RegistryComparisonCommand(service).run(args, System.out, System.err);
    } catch (IllegalArgumentException e) {
      System.err.println("Invalid configuration. Set REGISTRY_URL (HTTP(S) origin), DB_URL (jdbc:postgresql:...), DB_USER and DB_PASSWORD. Optional REGISTRY_TIMEOUT_SECONDS: 1–120; REGISTRY_MAX_BYTES: positive integer.");
      return 2;
    }
  }
  private static String required(Map<String,String> environment, String name) {
    String value = environment.get(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException();
    return value;
  }
}
