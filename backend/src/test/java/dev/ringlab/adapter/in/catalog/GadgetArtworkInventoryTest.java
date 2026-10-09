package dev.ringlab.adapter.in.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline catalog-to-asset contract. The reviewed ledger makes any artwork gap an explicit change. */
class GadgetArtworkInventoryTest {
  private static final Path PUBLIC = Path.of("../frontend/public").toAbsolutePath().normalize();
  private static final Set<String> REVIEWED_MISSING = Set.of();

  @Test void everyCanonicalIconMatchesTheReviewedIdentityAndLocalBytes() throws Exception {
    var gadgets = new GameDataCsvReader().read(Path.of("../game-data")).gadgets();
    var inventory = new ObjectMapper().readTree(Path.of("../game-data/review/gadget-artwork.json").toFile());
    assertEquals(117, gadgets.size()); assertEquals(gadgets.size(), inventory.size());
    var entries = new HashMap<String, com.fasterxml.jackson.databind.JsonNode>();
    for (var entry : inventory) assertNull(entries.put(entry.get("id").asText(), entry), "Duplicate inventory ID");
    var paths = new HashSet<String>(); var hashes = new HashSet<String>(); var missing = new HashSet<String>();
    for (var row : gadgets) {
      var gadget = row.value(); var entry = entries.remove(gadget.id().toString());
      assertNotNull(entry, gadget.name()); assertEquals(gadget.name(), entry.get("name").asText());
      if (gadget.imagePath() == null) { missing.add(gadget.id().toString()); continue; }
      assertEquals(gadget.imagePath(), entry.get("imagePath").asText(), gadget.name());
      assertEquals("canonical icon", entry.get("status").asText());
      assertTrue(paths.add(gadget.imagePath()), "Unrelated gadgets share a path: " + gadget.name());
      var asset = localAsset(gadget.imagePath());
      var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(asset)));
      assertEquals(entry.get("sha256").asText(), hash, gadget.name());
      assertTrue(hashes.add(hash), "Unrelated gadgets share artwork bytes: " + gadget.name());
      assertFalse(entry.get("sourcePage").asText().isBlank());
      assertFalse(entry.get("attribution").asText().isBlank());
      if (entry.get("newlySourced").asBoolean()) {
        assertTrue(entry.get("sourceImageUrl").asText().startsWith("https://img.atwiki.jp/sonicracingcw/attach/"));
        assertEquals("2026-10-09", entry.get("retrievedAt").asText());
      }
    }
    assertTrue(entries.isEmpty()); assertEquals(REVIEWED_MISSING, missing);
  }

  @Test void brokenAndExternalPathsFailTheCoverageGuard() {
    assertThrows(AssertionError.class, () -> localAsset("/assets/gadgets/missing-reviewed-icon.png"));
    assertThrows(AssertionError.class, () -> localAsset("https://example.com/icon.png"));
    assertThrows(AssertionError.class, () -> localAsset("/assets/../../outside.png"));
  }

  private static Path localAsset(String path) {
    assertTrue(path.startsWith("/assets/gadgets/"), "Expected a local gadget icon: " + path);
    var asset = PUBLIC.resolve(path.substring(1)).normalize();
    assertTrue(asset.startsWith(PUBLIC)); assertTrue(Files.isRegularFile(asset), "Broken canonical path: " + path);
    return asset;
  }
}
