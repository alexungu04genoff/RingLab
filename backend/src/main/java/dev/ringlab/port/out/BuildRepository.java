package dev.ringlab.port.out;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildRanking;
import java.util.*;

public interface BuildRepository {
  record Filter(
      String search,
      UUID racerId,
      UUID machineId,
      UUID authorId,
      UUID gameVersionId) {}

  Optional<Build> find(UUID id);

  List<BuildRanking.Candidate> searchCandidates(Filter filter);

  List<Build> findAll(Collection<UUID> ids);

  void save(Build build);

  void delete(UUID id);
}
