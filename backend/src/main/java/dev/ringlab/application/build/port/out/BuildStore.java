package dev.ringlab.application.build.port.out;

import dev.ringlab.domain.build.Build;
import java.util.*;

public interface BuildStore {
  record Filter(
      String search,
      UUID racerId,
      UUID machineId,
      UUID authorId,
      String sort,
      int page,
      int size) {}

  record Page(List<Build> items, long total) {}

  Optional<Build> find(UUID id);

  Page list(Filter filter);

  void save(Build build);

  void delete(UUID id);
}
