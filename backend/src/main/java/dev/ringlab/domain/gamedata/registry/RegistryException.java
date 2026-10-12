package dev.ringlab.domain.gamedata.registry;

/** Safe operator diagnostics; never includes remote response bodies, credentials or JDBC URLs. */
public final class RegistryException extends RuntimeException {
  public enum Code { UNAVAILABLE, TIMEOUT, HTTP_ERROR, INVALID_RESPONSE, INVALID_PUBLICATION }
  private final Code code;
  public RegistryException(Code code, String message) { super(message); this.code = code; }
  public Code code() { return code; }
}
