package dev.ringlab.port.in;

public interface PasswordResetUseCase {
  String CONFIRMATION = "If an eligible account exists for that email, a password reset link has been sent.";

  void request(String email);
  void reset(String token, String password, String confirmation);
}
