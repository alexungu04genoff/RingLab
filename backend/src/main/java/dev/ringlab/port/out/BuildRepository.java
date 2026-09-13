package dev.ringlab.port.out;

import dev.ringlab.domain.build.Build;
import java.util.*;

public interface BuildRepository {
  record Filter(
      String search,
      UUID racerId,
      UUID machineId,
      UUID authorId,
      UUID gameVersionId) {}

  Optional<Build> find(UUID id);

  List<Build> search(Filter filter);

  void save(Build build);

  void delete(UUID id);
}
