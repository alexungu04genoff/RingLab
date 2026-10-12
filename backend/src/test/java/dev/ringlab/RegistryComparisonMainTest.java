package dev.ringlab;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistryComparisonMainTest {
  @Test void rejectsMissingAndInvalidConfigurationBeforeOpeningConnections() {
    assertEquals(2, RegistryComparisonMain.run(new String[]{"apply"}, Map.of()));
    assertEquals(2, RegistryComparisonMain.run(new String[]{"compare","unused.json"}, Map.of()));
    assertEquals(2, RegistryComparisonMain.run(new String[]{"compare","unused.json"}, Map.of("REGISTRY_URL","http://localhost", "DB_URL","file:invalid")));
    var values = new java.util.HashMap<>(Map.of("REGISTRY_URL","http://localhost", "DB_URL","jdbc:postgresql://localhost/unreachable", "DB_USER","test", "DB_PASSWORD","test"));
    values.put("REGISTRY_TIMEOUT_SECONDS","invalid");
    assertEquals(2, RegistryComparisonMain.run(new String[]{"compare","unused.json"}, values));
  }
}
