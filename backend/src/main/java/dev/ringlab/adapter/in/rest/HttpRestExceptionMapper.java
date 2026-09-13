package dev.ringlab.adapter.in.rest;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class HttpRestExceptionMapper implements ExceptionMapper<WebApplicationException> {
  public Response toResponse(WebApplicationException exception) {
    int status = exception.getResponse().getStatus();
    if (status == 404 && hasInvalidParameterCause(exception)) status = 400;
    Response.Status knownStatus = Response.Status.fromStatusCode(status);
    String message = knownStatus == null ? "Request rejected" : knownStatus.getReasonPhrase();
    var response = Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
        .entity(new ErrorRestExceptionMapper.ErrorResponse(message));
    for (String header : new String[] {"Allow", "WWW-Authenticate", "Retry-After"}) {
      String value = exception.getResponse().getHeaderString(header);
      if (value != null) response.header(header, value);
    }
    return response.build();
  }

  private boolean hasInvalidParameterCause(Throwable exception) {
    for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
      if (cause instanceof IllegalArgumentException) return true;
    }
    return false;
  }
}
