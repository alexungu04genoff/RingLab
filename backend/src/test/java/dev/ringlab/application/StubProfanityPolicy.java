package dev.ringlab.application;

import dev.ringlab.application.validation.ProfanityPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class StubProfanityPolicy extends ProfanityPolicy {
  record Check(String text, String field) {}

  final List<Check> checks = new ArrayList<>();
  final Set<String> blocked = new HashSet<>();

  StubProfanityPolicy() {
    super((language, text) -> false);
  }

  @Override
  public boolean containsProfanity(String text) {
    checks.add(new Check(text, null));
    return blocked.contains(text);
  }

  @Override
  public void requireClean(String text) {
    checks.add(new Check(text, null));
    if (blocked.contains(text)) throw new ValidationException("Text contains inappropriate language");
  }

  @Override
  public void requireClean(String text, String field) {
    checks.add(new Check(text, field));
    if (blocked.contains(text)) {
      throw new ValidationException("Text contains inappropriate language", field);
    }
  }
}
