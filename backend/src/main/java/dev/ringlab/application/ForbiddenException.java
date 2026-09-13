package dev.ringlab.application;

import java.util.UUID;

public final class ForbiddenException extends AppException {
  public ForbiddenException(String message) {
    super(message);
  }

  public static void requireOwner(UUID owner, UUID actor) {
    if (!owner.equals(actor)) {
      throw new ForbiddenException("Only the author may change this resource");
    }
  }
}
