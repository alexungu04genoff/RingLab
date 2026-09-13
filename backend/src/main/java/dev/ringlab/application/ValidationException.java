package dev.ringlab.application;

public final class ValidationException extends AppException {
  private final String field;

  public ValidationException(String message) {
    this(message, null);
  }

  public ValidationException(String message, String field) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
