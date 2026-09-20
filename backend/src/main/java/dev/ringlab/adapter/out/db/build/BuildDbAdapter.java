package dev.ringlab.adapter.out.db.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.out.db.gamedata.GadgetDbEntity;
import dev.ringlab.adapter.out.db.gamedata.MachineDbEntity;
import dev.ringlab.adapter.out.db.gamedata.MachinePartDbEntity;
import dev.ringlab.adapter.out.db.gamedata.RacerDbEntity;
import dev.ringlab.domain.build.Build;
import dev.ringlab.application.NotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import dev.ringlab.domain.build.ranking.BuildRanking;
import dev.ringlab.port.out.BuildRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class BuildDbAdapter implements BuildRepository {
  private final EntityManager em;
  private final BuildDbMapper mapper;

  public Optional<Build> find(UUID id) {
    return Optional.ofNullable(mapper.toDomain(em.find(BuildDbEntity.class, id)));
  }

  public List<BuildRanking.Candidate> searchCandidates(Filter filter) {
    CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();

    CriteriaQuery<BuildRanking.Candidate> itemQuery = criteriaBuilder.createQuery(BuildRanking.Candidate.class);
    Root<BuildDbEntity> itemRoot = itemQuery.from(BuildDbEntity.class);
    itemQuery.select(criteriaBuilder.construct(
        BuildRanking.Candidate.class, itemRoot.get("id"), itemRoot.get("createdAt")));
    itemQuery.where(filters(criteriaBuilder, itemQuery, itemRoot, filter).toArray(Predicate[]::new));
    return em.createQuery(itemQuery).getResultList();
  }

  public List<Build> findAll(java.util.Collection<UUID> ids) {
    if (ids.isEmpty()) return List.of();
    CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
    CriteriaQuery<BuildDbEntity> query = criteriaBuilder.createQuery(BuildDbEntity.class);
    Root<BuildDbEntity> root = query.from(BuildDbEntity.class);
    query.where(root.get("id").in(ids));
    return em.createQuery(query).getResultList().stream().map(mapper::toDomain).toList();
  }

  private List<Predicate> filters(
      CriteriaBuilder criteriaBuilder, CriteriaQuery<?> query, Root<BuildDbEntity> build, Filter filter) {
    List<Predicate> predicates = new ArrayList<>();
    if (!filter.excludedIds().isEmpty()) {
      predicates.add(criteriaBuilder.not(build.get("id").in(filter.excludedIds())));
    }
    if (filter.gameVersionId() != null) {
      predicates.add(criteriaBuilder.equal(build.get("gameVersionId"), filter.gameVersionId()));
    }
    if (filter.search() != null && !filter.search().isBlank()) {
      String search = filter.search().toLowerCase(Locale.ROOT);
      Subquery<UUID> matchingRacers = query.subquery(UUID.class);
      Root<RacerDbEntity> racer = matchingRacers.from(RacerDbEntity.class);
      matchingRacers.select(racer.get("id"))
          .where(literalContains(criteriaBuilder, racer.get("name"), search));

      Subquery<UUID> matchingParts = query.subquery(UUID.class);
      Root<MachinePartDbEntity> part = matchingParts.from(MachinePartDbEntity.class);
      Root<MachineDbEntity> machine = matchingParts.from(MachineDbEntity.class);
      matchingParts.select(part.get("id")).where(
          criteriaBuilder.equal(part.get("sourceMachineId"), machine.get("id")),
          literalContains(criteriaBuilder, machine.get("name"), search));

      Subquery<UUID> matchingGadgetBuilds = query.subquery(UUID.class);
      Root<BuildDbEntity> gadgetBuild = matchingGadgetBuilds.from(BuildDbEntity.class);
      Join<BuildDbEntity, UUID> gadgetId = gadgetBuild.join("gadgetIds");
      Root<GadgetDbEntity> gadget = matchingGadgetBuilds.from(GadgetDbEntity.class);
      matchingGadgetBuilds.select(gadgetBuild.get("id")).where(
          criteriaBuilder.equal(gadgetId, gadget.get("id")),
          literalContains(criteriaBuilder, gadget.get("name"), search));

      predicates.add(criteriaBuilder.or(
          literalContains(criteriaBuilder, build.get("title"), search),
          build.get("racerId").in(matchingRacers),
          build.get("frontPartId").in(matchingParts),
          build.get("rearPartId").in(matchingParts),
          build.get("tirePartId").in(matchingParts),
          build.get("id").in(matchingGadgetBuilds)));
    }
    if (filter.racerId() != null) {
      predicates.add(criteriaBuilder.equal(build.get("racerId"), filter.racerId()));
    }
    if (filter.machineId() != null) {
      Subquery<UUID> sourceParts = query.subquery(UUID.class);
      Root<MachinePartDbEntity> part = sourceParts.from(MachinePartDbEntity.class);
      sourceParts.select(part.get("id"))
          .where(criteriaBuilder.equal(part.get("sourceMachineId"), filter.machineId()));
      predicates.add(criteriaBuilder.or(
          build.get("frontPartId").in(sourceParts),
          build.get("rearPartId").in(sourceParts),
          build.get("tirePartId").in(sourceParts)));
    }
    if (filter.authorId() != null) {
      predicates.add(criteriaBuilder.equal(build.get("authorId"), filter.authorId()));
    }
    return predicates;
  }

  private Predicate literalContains(CriteriaBuilder criteriaBuilder, Expression<String> value, String search) {
    return criteriaBuilder.greaterThan(criteriaBuilder.locate(criteriaBuilder.lower(value), search), 0);
  }

  public void save(Build build) {
    try {
      em.merge(mapper.toEntity(build));
      em.flush();
    } catch (ConstraintViolationException exception) {
      if ("23503".equals(exception.getSQLState())
          && "builds_remixed_from_build_id_fkey".equals(exception.getConstraintName())) {
        throw NotFoundException.missing("Remix source build");
      }
      throw exception;
    }
  }

  public void delete(UUID id) {
    em.remove(em.find(BuildDbEntity.class, id));
  }
}
