package dev.ringlab.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.AppException;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import org.junit.jupiter.api.Test;

class ErrorRestExceptionMapperTest {
  private final ErrorRestExceptionMapper mapper = new ErrorRestExceptionMapper();

  @Test
  void mapsSemanticApplicationExceptionsToHttpStatuses() {
    assertMapping(400, new ValidationException("Invalid"));
    assertMapping(401, new AuthenticationException("Unauthenticated"));
    assertMapping(403, new ForbiddenException("Forbidden"));
    assertMapping(404, new NotFoundException("Missing"));
    assertMapping(409, new AlreadyExistsException("Duplicate"));
    assertMapping(503, new ExternalServiceUnavailableException("Unavailable"));
  }

  private void assertMapping(int expectedStatus, AppException exception) {
    try (var response = mapper.toResponse(exception)) {
      assertEquals(expectedStatus, response.getStatus());
      assertEquals(
          new ErrorRestExceptionMapper.ErrorResponse(exception.getMessage()), response.getEntity());
    }
  }
}
