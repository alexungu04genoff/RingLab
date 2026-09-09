package dev.ringlab.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class UnexpectedExceptionRestExceptionMapperTest {
  @Test
  void returnsSafeGenericResponseForUnexpectedExceptions() {
    Response response = new UnexpectedExceptionRestExceptionMapper().toResponse(new IllegalStateException());

    assertEquals(500, response.getStatus());
    assertEquals(
        new ErrorRestExceptionMapper.ErrorResponse("An unexpected server error occurred"),
        response.getEntity());
  }
}
