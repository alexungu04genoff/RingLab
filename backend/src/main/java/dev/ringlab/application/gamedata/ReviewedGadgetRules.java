package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GadgetRuleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/**
 * The published API exposes one reviewed patch. Published facts are immutable, so a successful
 * detached snapshot lasts for this application instance. Restart with a reviewed version change
 * to select new facts; import alone does not grant review permission. Failed loads remain retryable.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ReviewedGadgetRules {
  private final GadgetRuleRepository repository;
  private GadgetRuleSnapshot snapshot;

  public synchronized GadgetRuleSnapshot snapshot() {
    if (snapshot == null) {
      snapshot = repository.findByVersion(PassiveGadgetRules.VERSION)
          .orElseThrow(() -> new IllegalStateException("Reviewed gadget rule snapshot is missing; apply the schema migrations"));
    }
    return snapshot;
  }
}
