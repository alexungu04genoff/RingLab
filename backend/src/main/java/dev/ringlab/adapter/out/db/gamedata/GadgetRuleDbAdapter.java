package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GadgetRuleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class GadgetRuleDbAdapter implements GadgetRuleRepository {
  private final DataSource source;
  @Inject public GadgetRuleDbAdapter(AgroalDataSource source) { this.source = source; }
  public GadgetRuleDbAdapter(DataSource source) { this.source = source; }

  @Override public Optional<GadgetRuleSnapshot> findByVersion(String version) {
    try (var c = source.getConnection(); var s = c.prepareStatement("SELECT id,version,released_at FROM game_versions WHERE version=?")) {
      s.setString(1, version);
      try (var r = s.executeQuery()) {
        if (!r.next()) return Optional.empty();
        var patch = new GameVersion(r.getObject("id", UUID.class), r.getString("version"), r.getObject("released_at", LocalDate.class));
        return new GameDataRuleStorage().read(c, patch.id()).stream().findFirst().map(rows -> rows.snapshot(patch));
      }
    } catch (SQLException e) { throw new IllegalStateException("Cannot load reviewed gadget rules (SQLSTATE " + e.getSQLState() + ")", e); }
  }
}
