package dev.ringlab.port.out;

/** Delivers the one-time link after its account and hashed token have been persisted. */
public interface EmailVerificationSender {
  void sendVerification(String email, String verificationUrl);
}
