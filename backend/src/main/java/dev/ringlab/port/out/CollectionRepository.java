package dev.ringlab.port.out;

import dev.ringlab.domain.collection.CollectionCategory;
import dev.ringlab.domain.collection.CollectionExclusions;
import java.util.UUID;

public interface CollectionRepository {
  CollectionExclusions load(UUID userId);
  void setOwned(UUID userId, CollectionCategory category, UUID itemId, boolean owned);
}
