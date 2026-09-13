package dev.ringlab.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class HttpRestExceptionMapperTest {
  private final HttpRestExceptionMapper mapper = new HttpRestExceptionMapper();

  @Test
  void sanitizesFrameworkMessagesAndDistinguishesMissingFromMalformedPaths() {
    try (var missing = mapper.toResponse(new NotFoundException("private internals"));
         var malformed = mapper.toResponse(new NotFoundException(new IllegalArgumentException("bad UUID")))) {
      assertEquals(404, missing.getStatus());
      assertEquals(new ErrorRestExceptionMapper.ErrorResponse("Not Found"), missing.getEntity());
      assertEquals(400, malformed.getStatus());
      assertEquals(new ErrorRestExceptionMapper.ErrorResponse("Bad Request"), malformed.getEntity());
    }
  }

  @Test
  void preservesProtocolHeadersWithoutCopyingUnsafeBodies() {
    var exception = new WebApplicationException(Response.status(405).header("Allow", "GET")
        .entity("internal details").build());
    try (var response = mapper.toResponse(exception)) {
      assertEquals(405, response.getStatus());
      assertEquals("GET", response.getHeaderString("Allow"));
      assertEquals(new ErrorRestExceptionMapper.ErrorResponse("Method Not Allowed"), response.getEntity());
    }
  }

  @Test
  void retainsAnExplicitUnavailableStatusAndRetryDelay() {
    var exception = new ServiceUnavailableException("Private upstream details", 10L);
    try (var response = mapper.toResponse(exception)) {
      assertEquals(503, response.getStatus());
      assertEquals("10", response.getHeaderString("Retry-After"));
      assertEquals(new ErrorRestExceptionMapper.ErrorResponse("Service Unavailable"), response.getEntity());
    }
  }
}
