package dev.ringlab.application.auth;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.domain.auth.ExternalIdentity;
import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.auth.VerifiedExternalIdentity;
import dev.ringlab.port.out.ExternalIdentityRepository;
import dev.ringlab.port.out.ExternalIdentityVerifier;
import dev.ringlab.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class ExternalAuthService {
  private final ExternalIdentityVerifier verifier;
  private final ExternalIdentityRepository identities;
  private final UserRepository users;
  private final ExternalAccountRegistration registration;

  @Transactional
  public User login(String credential) {
    var verified = verify(credential);
    var existing = identities.find(verified.provider(), verified.subject());
    if (existing.isPresent())
      return users.byId(existing.get().userId())
          .orElseThrow(() -> new AuthenticationException("Account unavailable"));

    String email = verified.email().toLowerCase(Locale.ROOT);
    var matchingAccount = users.byEmail(email);
    User user = matchingAccount.isPresent()
        ? accountForEmailLink(matchingAccount.get(), verified, email)
        : registration.create(email);
    identities.create(
        new ExternalIdentity(user.id(), verified.provider(), verified.subject(), Instant.now()));
    return user;
  }

  private User accountForEmailLink(User user, VerifiedExternalIdentity verified, String email) {
    // Google controls Gmail addresses. A verified third-party email claim alone is insufficient.
    // Requiring prior RingLab verification also avoids adopting an unverified local registration.
    if ("GOOGLE".equals(verified.provider()) && email.endsWith("@gmail.com")) {
      if (user.emailVerifiedAt() != null) return user;
      throw new AlreadyExistsException(
          "An unverified account already uses this Gmail address. Resend the verification email, "
              + "open its link, then try Google again.");
    }
    throw new AlreadyExistsException(
        "An account already exists with this email. Sign in with your username and password, "
            + "then link Google from your account page.");
  }

  @Transactional
  public void link(UUID userId, String credential) {
    var user = users.byId(userId)
        .orElseThrow(() -> new AuthenticationException("Account unavailable"));
    var verified = verify(credential);
    if (!user.email().equalsIgnoreCase(verified.email()))
      throw new ForbiddenException("Google account email must match your RingLab email.");

    var existing = identities.find(verified.provider(), verified.subject());
    if (existing.isPresent()) {
      if (existing.get().userId().equals(userId)) return;
      throw new AlreadyExistsException(
          "This Google account is already linked to another RingLab account.");
    }
    identities.create(
        new ExternalIdentity(userId, verified.provider(), verified.subject(), Instant.now()));
  }

  private VerifiedExternalIdentity verify(String credential) {
    if (credential == null || credential.isBlank() || credential.length() > 16384)
      throw new AuthenticationException("Invalid sign-in credential");
    return verifier.verify(credential);
  }
}
