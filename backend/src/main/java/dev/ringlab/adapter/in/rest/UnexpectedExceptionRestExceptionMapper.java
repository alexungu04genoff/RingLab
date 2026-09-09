package dev.ringlab.adapter.in.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.jbosslog.JBossLog;

@Provider
@JBossLog
public class UnexpectedExceptionRestExceptionMapper implements ExceptionMapper<Exception> {
  public Response toResponse(Exception exception) {
    log.error("Unexpected server exception", exception);
    return Response.serverError()
        .entity(new ErrorRestExceptionMapper.ErrorResponse("An unexpected server error occurred"))
        .build();
  }
}
