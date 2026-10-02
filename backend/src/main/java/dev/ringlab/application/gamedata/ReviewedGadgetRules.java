package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GadgetRuleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

/** The published API currently exposes one reviewed patch. Import does not grant review permission. */
@ApplicationScoped
@RequiredArgsConstructor
public class ReviewedGadgetRules {
  private final GadgetRuleRepository repository;

  public GadgetRuleSnapshot snapshot() {
    return repository.findByVersion(PassiveGadgetRules.VERSION)
        .orElseThrow(() -> new IllegalStateException("Reviewed gadget rule snapshot is missing; apply the schema migrations"));
  }
}
