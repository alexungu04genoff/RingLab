package dev.ringlab.application.collection;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.collection.*;
import dev.ringlab.port.out.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class CollectionService {
  private final CollectionRepository collection;
  private final GameDataRepository catalog;

  @Transactional
  public CollectionExclusions load(UUID actor) { return collection.load(Objects.requireNonNull(actor)); }

  @Transactional
  public void setOwned(UUID actor, CollectionCategory category, UUID id, boolean owned) {
    Objects.requireNonNull(actor);
    boolean exists = switch (category) {
      case RACER -> catalog.findRacer(id).isPresent();
      case MACHINE -> catalog.findMachine(id).isPresent();
      case GADGET -> catalog.findGadget(id).isPresent();
    };
    if (!exists) throw new ValidationException("Unknown collection item");
    collection.setOwned(actor, category, id, owned);
  }
}
