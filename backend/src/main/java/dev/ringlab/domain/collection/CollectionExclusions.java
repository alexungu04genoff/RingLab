package dev.ringlab.domain.collection;

import java.util.Set;
import java.util.UUID;

/** Absence means owned, including catalog entries added after an account was created. */
public record CollectionExclusions(Set<UUID> racers, Set<UUID> machines, Set<UUID> gadgets) {
  public static final CollectionExclusions NONE = new CollectionExclusions(Set.of(), Set.of(), Set.of());
  public CollectionExclusions {
    racers = Set.copyOf(racers);
    machines = Set.copyOf(machines);
    gadgets = Set.copyOf(gadgets);
  }
  public boolean racerAvailable(UUID id) { return !racers.contains(id); }
  public boolean machineAvailable(UUID id) { return !machines.contains(id); }
  public boolean gadgetAvailable(UUID id) { return !gadgets.contains(id); }
}
