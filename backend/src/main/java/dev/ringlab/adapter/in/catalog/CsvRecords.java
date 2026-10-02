package dev.ringlab.adapter.in.catalog;

import dev.ringlab.domain.gamedata.importing.ImportValidationException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Strict comma CSV: quoted fields, escaped quotes, CRLF/LF, and UTF-8 BOM. */
final class CsvRecords {
  record Record(int line, List<String> fields) {}

  static List<Record> read(Reader reader, String file) throws IOException {
    var result = new ArrayList<Record>();
    var fields = new ArrayList<String>();
    var field = new StringBuilder();
    boolean quoted = false, closed = false, first = true, previousCr = false;
    int line = 1, start = 1;
    for (int next; (next = reader.read()) != -1;) {
      char c = (char) next;
      if (first && c == '\ufeff') { first = false; continue; }
      first = false;
      if (previousCr && c == '\n') { previousCr = false; continue; }
      previousCr = c == '\r';
      if (c == '\r') c = '\n';
      if (c == '\0') throw invalid(file, line, "NUL is not valid CSV text");
      if (quoted) {
        if (c == '"') { quoted = false; closed = true; }
        else { field.append(c); if (c == '\n') line++; }
      } else if (closed && c == '"') {
        field.append('"'); quoted = true; closed = false;
      } else if (c == ',' || c == '\n') {
        fields.add(field.toString()); field.setLength(0); closed = false;
        if (c == '\n') {
          result.add(new Record(start, List.copyOf(fields))); fields.clear(); start = ++line;
        }
      } else if (closed) {
        throw invalid(file, line, "Expected comma or newline after closing quote");
      } else if (c == '"') {
        if (!field.isEmpty()) throw invalid(file, line, "Quote inside an unquoted field");
        quoted = true;
      } else field.append(c);
    }
    if (quoted) throw invalid(file, start, "Unterminated quoted field");
    if (closed || !field.isEmpty() || !fields.isEmpty()) {
      fields.add(field.toString()); result.add(new Record(start, List.copyOf(fields)));
    }
    return result;
  }

  private static ImportValidationException invalid(String file, int line, String reason) {
    return new ImportValidationException(List.of(file + ":" + line
        + " field=csv value=\"<record>\": " + reason));
  }
}
