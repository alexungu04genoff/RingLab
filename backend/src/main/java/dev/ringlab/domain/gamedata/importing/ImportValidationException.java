package dev.ringlab.domain.gamedata.importing;

import java.util.List;

public final class ImportValidationException extends IllegalArgumentException {
  private final List<String> problems;

  public ImportValidationException(List<String> problems) {
    super(String.join(System.lineSeparator(), problems));
    this.problems = List.copyOf(problems);
  }

  public List<String> problems() { return problems; }
}
