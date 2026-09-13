package dev.ringlab;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Loopback SMTP capture shared by in-process and packaged authentication contracts. */
public class VerificationMailResource implements QuarkusTestResourceLifecycleManager {
  private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();
  private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]{43})");
  private ServerSocket server;
  private ExecutorService connections;

  @Override
  public Map<String, String> start() {
    try {
      TOKENS.clear();
      server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
      connections = Executors.newVirtualThreadPerTaskExecutor();
      connections.submit(() -> {
        while (!server.isClosed()) {
          try {
            Socket socket = server.accept();
            connections.submit(() -> receive(socket));
          } catch (IOException failure) {
            if (!server.isClosed()) throw new UncheckedIOException(failure);
          }
        }
      });
      return Map.of("quarkus.mailer.host", "127.0.0.1",
          "quarkus.mailer.port", Integer.toString(server.getLocalPort()),
          "quarkus.mailer.mock", "false", "quarkus.mailer.tls", "false",
          "quarkus.mailer.start-tls", "DISABLED", "quarkus.mailer.login", "DISABLED");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  public static String tokenFor(String email) {
    String token = TOKENS.remove(email.toLowerCase(Locale.ROOT));
    if (token == null) throw new AssertionError("No verification email captured for test account");
    return token;
  }

  private void receive(Socket socket) {
    try (socket;
         var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         var output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
      socket.setSoTimeout(10_000);
      reply(output, "220 localhost test SMTP");
      String recipient = null;
      String line;
      while ((line = input.readLine()) != null) {
        String command = line.toUpperCase(Locale.ROOT);
        if (command.startsWith("EHLO") || command.startsWith("HELO")) {
          reply(output, "250 localhost");
        } else if (command.startsWith("RCPT TO:")) {
          recipient = line.substring(line.indexOf('<') + 1, line.indexOf('>')).toLowerCase(Locale.ROOT);
          reply(output, "250 OK");
        } else if (command.equals("DATA")) {
          reply(output, "354 End with a dot");
          StringBuilder message = new StringBuilder();
          while ((line = input.readLine()) != null && !line.equals(".")) message.append(line).append("\r\n");
          String content = message.toString();
          if (content.toLowerCase(Locale.ROOT).contains("content-transfer-encoding: base64")) {
            content = new String(Base64.getMimeDecoder().decode(content.split("\r\n\r\n", 2)[1]), StandardCharsets.UTF_8);
          } else {
            content = content.replace("=\r\n", "").replace("=3D", "=");
          }
          var token = TOKEN.matcher(content);
          if (recipient != null && token.find()) TOKENS.put(recipient, token.group(1));
          reply(output, "250 Captured");
        } else if (command.equals("QUIT")) {
          reply(output, "221 Bye");
          return;
        } else if (command.startsWith("MAIL FROM:") || command.equals("RSET") || command.equals("NOOP")) {
          reply(output, "250 OK");
        } else {
          reply(output, "502 Unsupported test SMTP command");
        }
      }
    } catch (IOException ignored) {
      // Idle pooled connections may close during resource shutdown.
    }
  }

  private static void reply(BufferedWriter output, String message) throws IOException {
    output.write(message + "\r\n");
    output.flush();
  }

  @Override
  public void stop() {
    try { if (server != null) server.close(); }
    catch (IOException failure) { throw new UncheckedIOException(failure); }
    finally {
      if (connections != null) connections.shutdownNow();
      TOKENS.clear();
    }
  }
}
