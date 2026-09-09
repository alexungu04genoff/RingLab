package dev.ringlab.adapter.in.rest;

import dev.ringlab.application.AppException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ErrorRestExceptionMapper implements ExceptionMapper<AppException> {
  public record ErrorResponse(String message) {}

  public Response toResponse(AppException e) {
    return Response.status(e.status).entity(new ErrorResponse(e.getMessage())).build();
  }
}
