package dev.ringlab.application.validation;

import dev.ringlab.application.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class ProfanityPolicy {
  private static final String MESSAGE = "Text contains inappropriate language";
  private static final String SEPARATOR = "[^\\p{L}\\p{N}]*";

  private final List<Pattern> blockedTerms;

  public ProfanityPolicy() {
    blockedTerms = loadBlockedTerms();
  }

  public boolean containsProfanity(String text) {
    if (text == null || text.isEmpty()) return false;
    String normalized = normalize(text);
    return blockedTerms.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
  }

  public void requireClean(String text) {
    if (containsProfanity(text)) throw new ValidationException(MESSAGE);
  }

  private static List<Pattern> loadBlockedTerms() {
    try (var input = ProfanityPolicy.class.getResourceAsStream("/profanity.txt")) {
      if (input == null) throw new IllegalStateException("Missing profanity word list");
      try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        return reader.lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(ProfanityPolicy::patternFor)
            .toList();
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not load profanity word list", exception);
    }
  }

  private static Pattern patternFor(String term) {
    String compact = normalize(term).replaceAll("[^\\p{L}\\p{N}]", "");
    if (compact.isEmpty()) throw new IllegalStateException("Invalid profanity word list entry");
    String letters = compact.codePoints()
        .mapToObj(codePoint -> Pattern.quote(new String(Character.toChars(codePoint))))
        .collect(Collectors.joining(SEPARATOR));
    return Pattern.compile("(?<![\\p{L}\\p{N}])" + letters + "(?![\\p{L}\\p{N}])");
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
}
