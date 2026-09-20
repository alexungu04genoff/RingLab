package dev.ringlab;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.nio.file.Path;
import java.util.Map;

/** Disables Quarkus's generated test keys before packaged-test augmentation. */
public class PackagedApiTestProfile implements QuarkusTestProfile {
  @Override
  public Map<String, String> getConfigOverrides() {
    Path directory = Path.of("target", "packaged-test-jwt").toAbsolutePath();
    return Map.of(
        "ringlab.public-base-url", "http://localhost:5173",
        "smallrye.jwt.sign.key.location", directory.resolve("private.pem").toString(),
        "mp.jwt.verify.publickey.location", directory.resolve("public.pem").toString());
  }
}
