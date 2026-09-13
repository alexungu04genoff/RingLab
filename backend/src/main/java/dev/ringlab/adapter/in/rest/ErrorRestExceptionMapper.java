package dev.ringlab.adapter.in.rest;

import dev.ringlab.application.AppException;
import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ErrorRestExceptionMapper implements ExceptionMapper<AppException> {
  public record ErrorResponse(String message) {}
  public record FieldErrorResponse(String message, String field) {}

  public Response toResponse(AppException e) {
    if (e instanceof ValidationException validation && validation.field() != null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new FieldErrorResponse(e.getMessage(), validation.field())).build();
    }
    return Response.status(statusFor(e)).entity(new ErrorResponse(e.getMessage())).build();
  }

  private Response.Status statusFor(AppException exception) {
    if (exception instanceof ValidationException) return Response.Status.BAD_REQUEST;
    if (exception instanceof AuthenticationException) return Response.Status.UNAUTHORIZED;
    if (exception instanceof ForbiddenException) return Response.Status.FORBIDDEN;
    if (exception instanceof NotFoundException) return Response.Status.NOT_FOUND;
    if (exception instanceof AlreadyExistsException) return Response.Status.CONFLICT;
    if (exception instanceof ExternalServiceUnavailableException)
      return Response.Status.SERVICE_UNAVAILABLE;
    throw new IllegalArgumentException("Unmapped application exception: " + exception.getClass());
  }
}
