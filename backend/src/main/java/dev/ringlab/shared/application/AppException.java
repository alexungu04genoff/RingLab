package dev.ringlab.shared.application;

public class AppException extends RuntimeException {
  public final int status;

  public AppException(int status, String message) {
    super(message);
    this.status = status;
  }

  public static AppException missing(String what) {
    return new AppException(404, what + " not found");
  }

  public static void requireOwner(java.util.UUID owner, java.util.UUID actor) {
    if (!owner.equals(actor))
      throw new AppException(403, "Only the author may change this resource");
  }
}
