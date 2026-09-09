import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Run with Java 21: java scripts/GenerateJwtKeys.java .keys */
class GenerateJwtKeys {
  public static void main(String[] args) throws Exception {
    Path directory = Path.of(args.length == 0 ? ".keys" : args[0]);
    Files.createDirectories(directory);
    Path privateKey = directory.resolve("private.pem");
    Path publicKey = directory.resolve("public.pem");
    if (Files.exists(privateKey) || Files.exists(publicKey)) {
      throw new IllegalStateException(
          "Keys already exist. Choose a different directory to avoid replacing them.");
    }
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    write(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
    write(publicKey, "PUBLIC KEY", pair.getPublic().getEncoded());
    System.out.println("JWT keys written to " + directory.toAbsolutePath());
  }

  private static void write(Path path, String label, byte[] encoded) throws Exception {
    String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    Files.writeString(
        path,
        "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n",
        StandardOpenOption.CREATE_NEW);
  }
}
