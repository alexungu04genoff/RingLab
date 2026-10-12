package dev.ringlab.adapter.in.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.ringlab.domain.gamedata.registry.*;
import dev.ringlab.port.in.CompareRegistryUseCase;
import java.io.*;
import java.nio.file.*;

/** Writes a local review artifact only; no APPLY option or approval token exists here. */
public final class RegistryComparisonCommand {
  private final CompareRegistryUseCase comparisons;
  public RegistryComparisonCommand(CompareRegistryUseCase comparisons) { this.comparisons = comparisons; }
  public record Report(String mode, int databaseWrites, String assessment, RegistryComparison comparison) {}

  public int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length != 2 || !args[0].equals("compare")) {
      err.println("Usage: RegistryComparisonMain compare <new-report.json>"); return 2;
    }
    try {
      Path report = Path.of(args[1]);
      if (Files.exists(report)) { err.println("Report already exists; choose a new output file."); return 2; }
      var result = comparisons.compareLatest();
      if (result.isEmpty()) { out.println("NO_PUBLICATIONS: Registry is empty. No database reads or writes performed."); return 0; }
      var comparison = result.orElseThrow(); var verified = comparison.registry(); var publication = verified.publication();
      // Serialize before opening the destination. CREATE_NEW protects existing reports, including concurrent writers.
      byte[] json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
          .writeValueAsBytes(new Report("READ_ONLY_COMPARISON", 0, comparison.assessment(), comparison));
      Files.write(report, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      out.println("Registry revision: " + publication.revision() + " (schema " + publication.schemaVersion() + ")");
      out.println("Registry game versions: " + publication.dataset().gameVersions().stream().map(Publication.GameVersion::version).sorted().toList());
      out.println("PostgreSQL game versions: " + comparison.databaseVersions());
      out.println("Publication checksum: " + publication.checksum());
      out.println("Registry dataset fingerprint: " + verified.datasetFingerprint());
      out.println("PostgreSQL comparison fingerprint: " + comparison.databaseFingerprint());
      out.println("Projected comparison fingerprint: " + comparison.projectionFingerprint());
      out.printf("Candidate differences: %d added rows, %d presentation updates, %d protected differences, %d incompatible mappings.%n",
          comparison.proposedAdditions(), comparison.proposedPresentationUpdates(), comparison.protectedChanges().size(), comparison.incompatibleMappings().size());
      out.println(comparison.assessment());
      out.println("Database writes: 0. No activation or calculation permission granted. These digests are not PLAN/APPLY approval tokens.");
      out.println("Full publication, differences and mapping observations: " + report.toAbsolutePath());
      return comparison.projectionCompatible() ? 0 : 2;
    } catch (RegistryException e) { err.println(e.code() + ": " + e.getMessage()); return 1; }
    catch (IOException e) { err.println("Cannot write comparison report. No database writes performed."); return 1; }
    catch (IllegalArgumentException | IllegalStateException e) {
      err.println("Comparison could not complete. Check the report path and PostgreSQL access/schema. No database writes performed."); return 1;
    }
  }
}
