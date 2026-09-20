package dev.ringlab.adapter.in.rest.community;

import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Formats only; never contacts Discord. Text budgets apply to the entire message. */
public final class DiscordTopBuildsFormatter {
  private DiscordTopBuildsFormatter() {}
  public record Mentions(List<String> parse) {}
  public record Thumbnail(String url) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Embed(String title, String url, String description, Thumbnail thumbnail) {}
  public record Message(String content, List<Embed> embeds, Mentions allowed_mentions) {}

  public static Message format(TopBuildsResponse snapshot) {
    var embeds = snapshot.items().stream().limit(3).map(item -> {
      var b = item.build();
      String description = "Author: " + safe(b.author().username(), 80)
          + "\nRacer: " + safe(b.racer().name(), 80) + " · " + item.machineType()
          + "\nPatch: " + (b.gameVersion() == null ? "Unspecified" : safe(b.gameVersion().version(), 40))
          + "\nVotes: ↑ " + b.upvotes() + " · ↓ " + b.downvotes() + " · Score " + b.score()
          + "\nFront: " + safe(b.frontPart().sourceMachineName(), 100)
          + "\nRear: " + safe(b.rearPart().sourceMachineName(), 100)
          + "\nTires: " + (b.tirePart() == null ? "None (Boost)" : safe(b.tirePart().sourceMachineName(), 100))
          + "\nGadgets: " + (b.gadgets().isEmpty() ? "None" : b.gadgets().stream()
              .map(g -> safe(g.name(), 80)).collect(Collectors.joining(", ")));
      // At most 3 * (256 + 1700) = 5868 embed text characters, below Discord's combined 6000.
      return new Embed("#" + item.rank() + " " + safe(b.title(), 250), item.buildUrl(),
          truncate(description, 1700), item.artworkUrl() == null ? null : new Thumbnail(item.artworkUrl()));
    }).toList();
    return new Message(embeds.isEmpty() ? "No eligible community builds are available yet."
        : "Top 3 community builds · Overall best rated · All patches", embeds, new Mentions(List.of()));
  }

  /** Remove formatting/control syntax and neutralize mentions and URL autolinking. */
  static String safe(String value, int limit) {
    var clean = new StringBuilder();
    value.codePoints().forEach(cp -> {
      if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) clean.append(' ');
      else if ("\\`*_~|[]<>()#".indexOf(cp) >= 0) clean.append(' ');
      else if (cp == '@') clean.append('＠');
      else if (cp == ':' || cp == '.') clean.append(cp == ':' ? '：' : '．');
      else clean.appendCodePoint(cp);
    });
    return truncate(clean.toString().strip(), limit);
  }

  private static String truncate(String text, int limit) {
    if (text.length() <= limit) return text;
    int end = limit - 1;
    if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
    return text.substring(0, end) + "…";
  }
}
