package dev.ringlab.application;

public abstract sealed class AppException extends RuntimeException
    permits AlreadyExistsException, AuthenticationException, ExternalServiceUnavailableException,
        ForbiddenException, NotFoundException, ValidationException {
  protected AppException(String message) {
    super(message);
  }
}
