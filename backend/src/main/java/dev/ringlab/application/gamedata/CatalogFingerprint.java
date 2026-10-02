package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.importing.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

final class CatalogFingerprint {
  static String approval(GameDataSet current, GameDataSet desired) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      value(digest, "ringlab-import-policy-v1");
      dataset(digest, current);
      dataset(digest, desired);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  static String normalized(Object value) {
    if (value == null) return null;
    return value instanceof BigDecimal n ? n.stripTrailingZeros().toPlainString() : value.toString();
  }

  private static void dataset(MessageDigest digest, GameDataSet d) {
    rows(digest, "racers", d.racers(), CatalogFields::racer);
    rows(digest, "machines", d.machines(), CatalogFields::machine);
    rows(digest, "parts", d.parts(), CatalogFields::part);
    rows(digest, "gadgets", d.gadgets(), CatalogFields::gadget);
    rows(digest, "maps", d.maps(), CatalogFields::map);
    rows(digest, "versions", d.versions(), CatalogFields::version);
    for (var s : d.snapshots().stream().sorted(Comparator.comparing(v -> v.versionId().toString())).toList()) {
      value(digest, s.versionId());
      rows(digest, "racer-stats", s.racers(), CatalogFields::stats);
      rows(digest, "part-stats", s.parts(), CatalogFields::stats);
    }
    value(digest, "end-dataset");
  }

  private static <T> void rows(MessageDigest digest, String name, List<CatalogRow<T>> rows,
                               Function<T, Map<String, Object>> fields) {
    value(digest, name);
    value(digest, rows.size());
    rows.stream().map(r -> fields.apply(r.value())).sorted(Comparator.comparing(r -> r.get("id").toString()))
        .forEach(row -> row.forEach((key, v) -> { value(digest, key); value(digest, v); }));
  }

  private static void value(MessageDigest digest, Object value) {
    String text = normalized(value);
    if (text == null) { digest.update("-1:".getBytes(StandardCharsets.UTF_8)); return; }
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    digest.update((bytes.length + ":").getBytes(StandardCharsets.UTF_8));
    digest.update(bytes);
  }
}
