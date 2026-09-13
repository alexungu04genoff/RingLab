package dev.ringlab.application;

public final class NotFoundException extends AppException {
  public NotFoundException(String message) {
    super(message);
  }

  public static NotFoundException missing(String resource) {
    return new NotFoundException(resource + " not found");
  }
}
