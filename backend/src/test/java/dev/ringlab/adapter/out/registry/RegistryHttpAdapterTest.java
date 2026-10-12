package dev.ringlab.adapter.out.registry;

import com.sun.net.httpserver.*;
import dev.ringlab.domain.gamedata.registry.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.ringlab.adapter.out.registry.CanonicalJson.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistryHttpAdapterTest {
  private HttpServer server;
  private ExecutorService executor;
  private final PublicationContract contract = new PublicationContract();
  private Publication publication;
  private Publication.Metadata metadata;
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private final List<String> conditionals = new CopyOnWriteArrayList<>();
  private HttpHandler handler;
  private RegistryHttpAdapter client;

  @BeforeEach void start() throws Exception {
    publication = contract.read(ContractTest.baselineBytes()); metadata = contract.metadata(publication);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
    handler = this::normal;
    server.createContext("/", exchange -> {
      requests.add(exchange.getRequestURI().getPath());
      conditionals.add(Objects.toString(exchange.getRequestHeaders().getFirst("If-None-Match"), "none"));
      try { handler.handle(exchange); } finally { exchange.close(); }
    });
    server.start(); client = new RegistryHttpAdapter(origin(), Duration.ofSeconds(5), 2_000_000);
  }
  @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
  private URI origin() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
  private boolean isMeta(HttpExchange e) { return e.getRequestURI().getPath().endsWith("/meta"); }
  private void normal(HttpExchange e) throws IOException {
    byte[] body = bytes(tree(isMeta(e) ? metadata : publication));
    String tag = isMeta(e) ? "\"meta-" + digest(tree(metadata)) + "\"" : "\"pub-" + publication.checksum() + "\"";
    e.getResponseHeaders().set("ETag", tag);
    if (tag.equals(e.getRequestHeaders().getFirst("If-None-Match"))) respond(e, 304, new byte[0]);
    else respond(e, 200, body);
  }
  private void respond(HttpExchange e, int status, byte[] body) throws IOException {
    e.getResponseHeaders().set("Content-Type", "application/json");
    e.sendResponseHeaders(status, status == 304 ? -1 : body.length);
    if (status != 304) e.getResponseBody().write(body);
  }
  private RegistryException rejected() { return assertThrows(RegistryException.class, client::latest); }

  @Test void validatesBaselineThenUsesBothVerifiedEtagsOn304() {
    var first = client.latest().orElseThrow(); var second = client.latest().orElseThrow();
    assertEquals(first, second); assertEquals(metadata.datasetFingerprint(), first.datasetFingerprint());
    assertEquals(List.of("/api/v1/publications/latest/meta", "/api/v1/publications/1",
        "/api/v1/publications/latest/meta", "/api/v1/publications/1"), requests);
    assertEquals("none", conditionals.getFirst()); assertEquals("none", conditionals.get(1));
    assertTrue(conditionals.get(2).startsWith("\"meta-")); assertEquals("\"pub-" + publication.checksum() + "\"", conditionals.get(3));
  }
  @Test void advancingLatestCannotChangeTheRevisionAlreadyAdvertised() {
    Publication previous = publication;
    var next = ContractTest.seal(tree(previous.dataset()), 2, previous.publishedBy(), "Next", Instant.parse(previous.publishedAt()));
    handler = e -> { normal(e); if (isMeta(e)) metadata = contract.metadata(next); };
    assertEquals(1, client.latest().orElseThrow().publication().revision());
    publication = next;
    assertEquals(2, client.latest().orElseThrow().publication().revision());
    assertEquals("/api/v1/publications/1", requests.get(1)); assertEquals("/api/v1/publications/2", requests.get(3));
    assertEquals("none", conditionals.get(3));
  }
  @Test void emptyRegistryClearsCacheAndNeverRequestsAPublication() {
    client.latest(); requests.clear();
    handler = e -> respond(e, 404, "{\"code\":\"NO_PUBLICATIONS\",\"message\":\"Empty\"}".getBytes(StandardCharsets.UTF_8));
    assertTrue(client.latest().isEmpty()); assertEquals(1, requests.size());
    handler = this::normal; client.latest(); assertEquals("none", conditionals.get(3));
  }
  @Test void outagesNeverReturnStaleCachedData() {
    client.latest(); handler = e -> respond(e, 503, new byte[]{'{','}'});
    assertEquals(RegistryException.Code.HTTP_ERROR, rejected().code());
    handler = this::normal; assertEquals(publication, client.latest().orElseThrow().publication());
    server.stop(0); assertEquals(RegistryException.Code.UNAVAILABLE, rejected().code());
  }
  @ParameterizedTest @ValueSource(strings={"fingerprint", "summary", "checksum", "publisher", "revision", "schema", "parent", "timestamp"})
  void metadataMustAgreeInEveryField(String field) {
    var changed = (com.fasterxml.jackson.databind.node.ObjectNode)tree(metadata);
    switch (field) {
      case "fingerprint" -> changed.put("datasetFingerprint", "0".repeat(64));
      case "summary" -> changed.put("changeSummary", "Different");
      case "checksum" -> changed.put("checksum", "0".repeat(64));
      case "publisher" -> ((com.fasterxml.jackson.databind.node.ObjectNode)changed.get("publishedBy")).put("displayName", "Other");
      case "revision" -> { changed.put("revision", 2); changed.put("parentRevision", 1); }
      case "schema" -> changed.put("schemaVersion", "2.0.0");
      case "parent" -> changed.put("parentRevision", 1);
      case "timestamp" -> changed.put("publishedAt", "2026-10-12T02:00:00Z");
    }
    handler = e -> {
      if (!isMeta(e)) { normal(e); return; }
      e.getResponseHeaders().set("ETag", "\"meta-" + digest(changed) + "\""); respond(e, 200, bytes(changed));
    };
    assertEquals(RegistryException.Code.INVALID_PUBLICATION, rejected().code());
  }
  @ParameterizedTest @ValueSource(strings={"unexpected304", "missingEtag", "wrongEtag", "encoding", "type", "redirect", "missingRevision", "malformed404", "badJson", "badChecksum", "duplicateMetadataKey"})
  void invalidResponsesFailClosed(String fault) {
    handler = e -> {
      if (fault.equals("unexpected304")) { respond(e, 304, new byte[0]); return; }
      if (fault.equals("redirect")) { e.getResponseHeaders().set("Location", origin() + "/elsewhere"); respond(e, 302, new byte[]{'{','}'}); return; }
      if (fault.equals("malformed404")) { respond(e, 404, "{\"code\":\"NOT_FOUND\",\"message\":\"No\"}".getBytes()); return; }
      if (fault.equals("duplicateMetadataKey") && isMeta(e)) {
        respond(e, 200, new String(bytes(tree(metadata)), StandardCharsets.UTF_8).replace("{", "{\"revision\":1,").getBytes(StandardCharsets.UTF_8)); return;
      }
      if (isMeta(e)) { normal(e); return; }
      if (fault.equals("missingRevision")) { respond(e, 404, new byte[]{'{','}'}); return; }
      byte[] body = bytes(tree(publication));
      e.getResponseHeaders().set("ETag", "\"pub-" + publication.checksum() + "\"");
      switch (fault) {
        case "missingEtag" -> e.getResponseHeaders().remove("ETag");
        case "wrongEtag" -> e.getResponseHeaders().set("ETag", "\"incorrect\"");
        case "encoding" -> e.getResponseHeaders().set("Content-Encoding", "gzip");
        case "badJson" -> body = new byte[]{'{'};
        case "badChecksum" -> body = new String(body, StandardCharsets.UTF_8).replace(publication.checksum(), "0".repeat(64)).getBytes(StandardCharsets.UTF_8);
        case "type" -> { e.getResponseHeaders().set("Content-Type", "text/html"); e.sendResponseHeaders(200, body.length); e.getResponseBody().write(body); return; }
      }
      respond(e, 200, body);
    };
    rejected();
  }
  @Test void boundedDownloadAndWholeBodyTimeout() {
    client = new RegistryHttpAdapter(origin(), Duration.ofSeconds(5), 1024); rejected();
    client = new RegistryHttpAdapter(origin(), Duration.ofMillis(100), 2_000_000);
    handler = e -> {
      e.sendResponseHeaders(200, 100); e.getResponseBody().write('{'); e.getResponseBody().flush();
      try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    };
    assertEquals(RegistryException.Code.TIMEOUT, rejected().code());
  }
  @Test void rejectsChangedImmutableRevisionEvenWithNewValidChecksums() {
    client.latest(); publication = ContractTest.seal(tree(publication.dataset()), 1, publication.publishedBy(), "Changed immutable metadata", Instant.parse(publication.publishedAt()));
    metadata = contract.metadata(publication);
    assertEquals(RegistryException.Code.INVALID_PUBLICATION, rejected().code());
  }
  @Test void invalidConfigurationCannotIssueRequests() {
    for (String value : List.of("localhost", "http://localhost:99999", "file:///tmp/data", "https://user:password@example.test", "https://example.test/api", "https://example.test/?key=value", "https://example.test/#secret"))
      assertThrows(IllegalArgumentException.class, () -> new RegistryHttpAdapter(URI.create(value), Duration.ofSeconds(1), 100));
    assertThrows(IllegalArgumentException.class, () -> new RegistryHttpAdapter(origin(), Duration.ZERO, 100));
    assertThrows(IllegalArgumentException.class, () -> new RegistryHttpAdapter(origin(), Duration.ofSeconds(121), 100));
    assertThrows(IllegalArgumentException.class, () -> new RegistryHttpAdapter(origin(), Duration.ofSeconds(1), 0));
    assertTrue(requests.isEmpty());
  }
  @Test void latestCannotRegressWithinAClientSession() {
    Publication previous = publication;
    publication = ContractTest.seal(tree(previous.dataset()), 2, previous.publishedBy(), "Next", Instant.parse(previous.publishedAt()));
    metadata = contract.metadata(publication); client.latest();
    publication = previous; metadata = contract.metadata(previous);
    assertEquals(RegistryException.Code.INVALID_PUBLICATION, rejected().code());
  }
  @Test void interruptedCallerRetainsInterruptAndDoesNotUseCache() {
    Thread.currentThread().interrupt();
    try { rejected(); assertTrue(Thread.currentThread().isInterrupted()); }
    finally { Thread.interrupted(); }
  }
}
