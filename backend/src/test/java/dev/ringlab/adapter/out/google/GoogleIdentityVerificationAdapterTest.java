package dev.ringlab.adapter.out.google;

import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.ExternalServiceUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoogleIdentityVerificationAdapterTest {
  private static final GsonFactory JSON = GsonFactory.getDefaultInstance();
  private static final String CLIENT = "test-client.apps.googleusercontent.com";

  private GoogleIdentityVerificationAdapter adapter() throws Exception {
    String certificates = JSON.toString(Map.of("test-key", resource("google-test-certificate.pem")));
    var transport = new MockHttpTransport.Builder().setLowLevelHttpResponse(
        new MockLowLevelHttpResponse().setContentType("application/json").setContent(certificates)).build();
    return new GoogleIdentityVerificationAdapter(new GooglePublicKeysManager(transport, JSON), CLIENT);
  }

  @Test
  void verifiesRealSignatureAndReturnsOnlyVerifiedIdentityFacts() throws Exception {
    var identity = adapter().verify(sign(payload(), privateKey()));
    assertEquals("GOOGLE", identity.provider());
    assertEquals("stable-subject", identity.subject());
    assertEquals("alex@example.test", identity.email());
  }

  @Test
  void rejectsExpiredWrongAudienceWrongIssuerAndUntrustedSignatures() throws Exception {
    var expired = payload(); expired.setExpirationTimeSeconds(Instant.now().getEpochSecond() - 10);
    var audience = payload(); audience.setAudience("another-client");
    var issuer = payload(); issuer.setIssuer("https://attacker.example");
    for (var payload : List.of(expired, audience, issuer))
      assertThrows(AuthenticationException.class, () -> adapter().verify(sign(payload, privateKey())));
    var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
    var attackerKey = generator.generateKeyPair().getPrivate();
    assertThrows(AuthenticationException.class, () -> adapter().verify(sign(payload(), attackerKey)));
  }

  @Test
  void rejectsMissingSubjectMissingOrUnverifiedEmailAndMalformedCredentials() throws Exception {
    var subject = payload(); subject.setSubject("");
    var email = payload(); email.setEmail(null);
    var unverified = payload(); unverified.setEmailVerified(false);
    for (var payload : List.of(subject, email, unverified))
      assertThrows(AuthenticationException.class, () -> adapter().verify(sign(payload, privateKey())));
    assertThrows(AuthenticationException.class, () -> adapter().verify("not-a-token"));
    assertThrows(ExternalServiceUnavailableException.class,
        () -> new GoogleIdentityVerificationAdapter(Optional.empty()).verify("credential"));
  }

  private GoogleIdToken.Payload payload() {
    var payload = new GoogleIdToken.Payload();
    payload.setIssuer("https://accounts.google.com");
    payload.setAudience(CLIENT);
    payload.setSubject("stable-subject");
    payload.setEmail("alex@example.test");
    payload.setEmailVerified(true);
    payload.setIssuedAtTimeSeconds(Instant.now().getEpochSecond() - 10);
    payload.setExpirationTimeSeconds(Instant.now().getEpochSecond() + 300);
    return payload;
  }

  private String sign(GoogleIdToken.Payload payload, PrivateKey key) throws Exception {
    var header = new JsonWebSignature.Header().setAlgorithm("RS256").setKeyId("test-key");
    return JsonWebSignature.signUsingRsaSha256(key, JSON, header, payload);
  }

  private PrivateKey privateKey() throws Exception {
    String pem = resource("google-test-private.pem").replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
  }

  private String resource(String name) throws Exception {
    try (var stream = getClass().getResourceAsStream("/" + name)) {
      assertNotNull(stream);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
