package dev.ringlab.adapter.in.rest.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;

final class ClientIpResolver {
  private ClientIpResolver() {}

  static String resolve(
      String immediatePeerAddress, String cloudflareClientIp, boolean trustCloudflareClientIp) {
    String peer = canonicalIp(immediatePeerAddress);
    if (trustCloudflareClientIp && isLoopback(peer)) {
      String cloudflareAddress = canonicalIp(cloudflareClientIp);
      if (cloudflareAddress != null) {
        return cloudflareAddress;
      }
    }
    return peer == null ? "unknown" : peer;
  }

  private static boolean isLoopback(String address) {
    if (address == null) {
      return false;
    }
    try {
      return InetAddress.getByName(address).isLoopbackAddress();
    } catch (UnknownHostException exception) {
      return false;
    }
  }

  private static String canonicalIp(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String candidate = value.trim();
    if (!candidate.matches("[0-9a-fA-F:.]+")) {
      return null;
    }
    try {
      return InetAddress.getByName(candidate).getHostAddress();
    } catch (UnknownHostException exception) {
      return null;
    }
  }
}
