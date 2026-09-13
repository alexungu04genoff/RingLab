package dev.ringlab;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;

/** Supplies an ephemeral RSA key pair to the packaged application process. */
public class JwtKeysTestResource implements QuarkusTestResourceLifecycleManager {
  private Path directory;

  @Override
  public Map<String, String> start() {
    try {
      directory = Path.of("target", "packaged-test-jwt").toAbsolutePath();
      Files.createDirectories(directory);
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var keyPair = generator.generateKeyPair();
      Path privateKey = directory.resolve("private.pem");
      Path publicKey = directory.resolve("public.pem");
      Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
      Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
      return Map.of(
          "smallrye.jwt.sign.key.location", privateKey.toAbsolutePath().toString(),
          "mp.jwt.verify.publickey.location", publicKey.toAbsolutePath().toString());
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("Could not generate JWT test keys", failure);
    }
  }

  private static String pem(String label, byte[] encoded) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
  }

  @Override
  public void stop() {
    if (directory == null) return;
    try (var files = Files.walk(directory)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
