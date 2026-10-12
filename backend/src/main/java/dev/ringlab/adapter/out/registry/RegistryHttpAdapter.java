package dev.ringlab.adapter.out.registry;

import dev.ringlab.domain.gamedata.registry.*;
import dev.ringlab.port.out.RegistryPublicationSource;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static dev.ringlab.adapter.out.registry.CanonicalJson.*;
import static dev.ringlab.domain.gamedata.registry.RegistryException.Code.*;

/** Explicit operator client, never a CDI bean. Only a fully verified pair enters the bounded memory cache. */
public final class RegistryHttpAdapter implements RegistryPublicationSource {
  private final URI base;
  private final Duration timeout;
  private final HttpClient client;
  private final PublicationContract contract = new PublicationContract();
  private final int maxBytes;
  private Publication cached;
  private Publication.Metadata cachedMetadata;

  public RegistryHttpAdapter(URI base, Duration timeout, int maxBytes) {
    if (base == null || base.getScheme() == null || !Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
        || base.getPort() > 65535
        || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
        || !(base.getPath().isEmpty() || base.getPath().equals("/")))
      throw new IllegalArgumentException("Registry URL must be an HTTP(S) origin without credentials, path, query or fragment.");
    if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(2)) > 0 || maxBytes < 1)
      throw new IllegalArgumentException("Use a positive timeout up to 120 seconds and a positive response size limit.");
    this.base = base; this.timeout = timeout; this.maxBytes = maxBytes;
    client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override public synchronized Optional<VerifiedRegistryPublication> latest() {
    try {
      String priorMetaTag = cachedMetadata == null ? null : metaTag(cachedMetadata);
      var metaResponse = get("/api/v1/publications/latest/meta", priorMetaTag, Math.min(maxBytes, 262144));
      if (metaResponse.statusCode() == 404) {
        var error = parse(metaResponse.body());
        if (!error.isObject() || error.size() != 2 || !error.path("code").asText().equals("NO_PUBLICATIONS")
            || !error.path("message").isTextual()) throw failure(INVALID_RESPONSE, "Invalid missing-publication response.");
        cached = null; cachedMetadata = null;
        return Optional.empty();
      }
      Publication.Metadata metadata;
      if (metaResponse.statusCode() == 304) {
        requireCached(cachedMetadata, metaResponse, priorMetaTag);
        metadata = cachedMetadata;
      } else {
        requireSuccess(metaResponse);
        metadata = contract.readMetadata(metaResponse.body());
        requireTag(metaResponse, metaTag(metadata));
      }
      if (cached != null && metadata.revision() < cached.revision())
        throw failure(INVALID_PUBLICATION, "Latest Registry revision regressed within this comparison session.");
      String priorPubTag = cached != null && cached.revision() == metadata.revision() ? pubTag(cached) : null;
      // Never fetch "latest" data: head changes cannot mix metadata with another revision.
      var response = get("/api/v1/publications/" + metadata.revision(), priorPubTag, maxBytes);
      Publication publication;
      if (response.statusCode() == 304) {
        requireCached(priorPubTag, response, priorPubTag);
        publication = cached;
      } else {
        requireSuccess(response);
        publication = contract.read(response.body());
        requireTag(response, pubTag(publication));
      }
      contract.verifyFingerprint(publication, metadata.datasetFingerprint());
      if (!contract.metadata(publication).equals(metadata))
        throw failure(INVALID_PUBLICATION, "Metadata does not match the immutable publication.");
      if (cached != null && cached.revision() == publication.revision() && !cached.equals(publication))
        throw failure(INVALID_PUBLICATION, "Registry changed an already verified immutable revision.");
      cached = publication; cachedMetadata = metadata;
      return Optional.of(new VerifiedRegistryPublication(publication, metadata.datasetFingerprint()));
    } catch (ContractException e) {
      throw failure(INVALID_PUBLICATION, "Registry contract verification failed: " + e.issues().getFirst().code()
          + " at " + e.issues().getFirst().path());
    }
  }

  private HttpResponse<byte[]> get(String path, String etag, int limit) {
    var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(timeout)
        .header("Accept", "application/json").header("Accept-Encoding", "identity").GET();
    if (etag != null) builder.header("If-None-Match", etag);
    var future = client.sendAsync(builder.build(), ignored -> new LimitedBody(limit));
    try { return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
    catch (TimeoutException e) { future.cancel(true); throw failure(TIMEOUT, "Registry request timed out."); }
    catch (InterruptedException e) {
      future.cancel(true); Thread.currentThread().interrupt(); throw failure(UNAVAILABLE, "Registry request interrupted.");
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RegistryException r) throw r;
      if (e.getCause() instanceof HttpTimeoutException) throw failure(TIMEOUT, "Registry request timed out.");
      throw failure(UNAVAILABLE, "Registry unavailable; existing PostgreSQL data remains in use.");
    }
  }
  private static String metaTag(Publication.Metadata m) { return "\"meta-" + digest(tree(m)) + "\""; }
  private static String pubTag(Publication p) { return "\"pub-" + p.checksum() + "\""; }
  private static void requireCached(Object value, HttpResponse<byte[]> response, String tag) {
    if (value == null || response.body().length != 0) throw failure(INVALID_RESPONSE, "304 requires a previously verified representation and no body.");
    requireTag(response, tag);
  }
  private static void requireTag(HttpResponse<byte[]> response, String tag) {
    if (!response.headers().allValues("ETag").equals(List.of(tag)))
      throw failure(INVALID_RESPONSE, "Missing or inconsistent Registry ETag.");
    if (!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity"))
      throw failure(INVALID_RESPONSE, "Only the v1 identity representation is supported.");
  }
  private static void requireSuccess(HttpResponse<byte[]> response) {
    if (response.statusCode() != 200) throw failure(HTTP_ERROR, "Registry returned HTTP " + response.statusCode() + ". No comparison performed.");
    if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
      throw failure(INVALID_RESPONSE, "Expected an application/json Registry response.");
  }
  private static RegistryException failure(RegistryException.Code code, String message) { return new RegistryException(code, message); }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final int limit;
    private Flow.Subscription subscription;
    LimitedBody(int limit) { this.limit = limit; }
    public CompletionStage<byte[]> getBody() { return result; }
    public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
    public void onNext(List<ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if (buffer.remaining() > limit - bytes.size()) {
          subscription.cancel(); result.completeExceptionally(failure(INVALID_RESPONSE, "Registry response exceeds the configured size limit.")); return;
        }
        byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }
    public void onError(Throwable error) { result.completeExceptionally(error); }
    public void onComplete() { result.complete(bytes.toByteArray()); }
  }
}
