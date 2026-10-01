package dev.ringlab.port.in;

/** Account activation, including verification delivery after the database transaction. */
public interface AccountRegistrationUseCase {
  void register(String username, String email, String password);
  void verifyEmail(String token);
  void resendVerification(String email);
}
