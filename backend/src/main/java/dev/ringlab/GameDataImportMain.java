package dev.ringlab;

import dev.ringlab.adapter.in.catalog.GameDataImportCommand;
import dev.ringlab.adapter.out.db.gamedata.GameDataImportDbAdapter;
import dev.ringlab.application.gamedata.GameDataImportService;
import org.postgresql.ds.PGSimpleDataSource;

/** Composition root for the one-shot command. Does not boot Quarkus or run Flyway. */
public final class GameDataImportMain {
  private GameDataImportMain() {}

  public static void main(String[] args) {
    var source = new PGSimpleDataSource();
    if (args.length > 0 && !args[0].equals("validate")) {
      String url = System.getenv("DB_URL");
      String user = System.getenv("DB_USER");
      String password = System.getenv("DB_PASSWORD");
      if (url == null || user == null || password == null || !url.startsWith("jdbc:postgresql:")) {
        System.err.println("Set DB_URL (jdbc:postgresql:...), DB_USER and DB_PASSWORD for plan/apply.");
        System.exit(2);
        return;
      }
      source.setURL(url);
      source.setUser(user);
      source.setPassword(password);
      source.setConnectTimeout(10);
    }
    var useCase = new GameDataImportService(new GameDataImportDbAdapter(source));
    System.exit(new GameDataImportCommand(useCase).run(args, System.out, System.err));
  }
}
