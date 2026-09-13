package dev.ringlab.adapter.out.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.domain.auth.VerifiedExternalIdentity;
import dev.ringlab.port.out.ExternalIdentityVerifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GoogleIdentityVerificationAdapter implements ExternalIdentityVerifier {
  private final GoogleIdTokenVerifier verifier;

  @Inject
  public GoogleIdentityVerificationAdapter(@ConfigProperty(name = "google.client-id") Optional<String> clientId) {
    this(new GooglePublicKeysManager(new NetHttpTransport(), GsonFactory.getDefaultInstance()), clientId.orElse(""));
  }

  GoogleIdentityVerificationAdapter(GooglePublicKeysManager keys, String clientId) {
    verifier = clientId.isBlank() ? null : new GoogleIdTokenVerifier.Builder(keys)
        .setAudience(List.of(clientId)).setAcceptableTimeSkewSeconds(0).build();
  }

  @Override
  public VerifiedExternalIdentity verify(String credential) {
    if (verifier == null)
      throw new ExternalServiceUnavailableException("Google sign-in is not configured");
    if (credential == null || credential.isBlank() || credential.length() > 16384) throw invalid();
    GoogleIdToken token;
    try {
      token = GoogleIdToken.parse(verifier.getJsonFactory(), credential);
    } catch (IOException | IllegalArgumentException e) {
      throw invalid();
    }
    try {
      if (token.getPayload().getExpirationTimeSeconds() == null
          || token.getPayload().getIssuedAtTimeSeconds() == null) throw invalid();
      if (!verifier.verify(token)) throw invalid();
      var payload = token.getPayload();
      String subject = payload.getSubject();
      String email = payload.getEmail();
      if (subject == null || subject.isBlank() || subject.length() > 255
          || email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
          || !Boolean.TRUE.equals(payload.getEmailVerified()) || payload.getExpirationTimeSeconds() == null)
        throw invalid();
      return new VerifiedExternalIdentity("GOOGLE", subject, email, (String) payload.get("name"));
    } catch (IOException e) {
      throw new ExternalServiceUnavailableException("Google sign-in is temporarily unavailable");
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw invalid();
    }
  }

  private AuthenticationException invalid() { return new AuthenticationException("Invalid Google credential"); }
}
