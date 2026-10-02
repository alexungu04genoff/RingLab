package dev.ringlab.domain.gamedata.importing;

/** Source location retained through parsing and semantic validation. */
public record CatalogRow<T>(String file, int row, T value) {
  public String problem(String field, Object badValue, String reason) {
    return file + ":" + row + " field=" + field + " value=\""
        + String.valueOf(badValue).replace("\n", "\\n").replace("\r", "\\r") + "\": " + reason;
  }
}
