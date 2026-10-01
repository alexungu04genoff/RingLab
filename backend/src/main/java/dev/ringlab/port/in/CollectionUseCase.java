package dev.ringlab.port.in;

import dev.ringlab.domain.collection.CollectionCategory;
import dev.ringlab.domain.collection.CollectionExclusions;
import java.util.UUID;

public interface CollectionUseCase {
  CollectionExclusions load(UUID actor);
  void setOwned(UUID actor, CollectionCategory category, UUID id, boolean owned);
}
