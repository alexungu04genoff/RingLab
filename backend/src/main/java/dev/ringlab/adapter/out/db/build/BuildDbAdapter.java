package dev.ringlab.adapter.out.db.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.out.db.vote.VoteDbEntity;
import dev.ringlab.adapter.out.db.gamedata.MachinePartDbEntity;
import dev.ringlab.domain.build.Build;
import dev.ringlab.port.out.BuildRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
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

  public Page list(Filter filter) {
    CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();

    CriteriaQuery<BuildDbEntity> itemQuery = criteriaBuilder.createQuery(BuildDbEntity.class);
    Root<BuildDbEntity> itemRoot = itemQuery.from(BuildDbEntity.class);
    itemQuery.where(filters(criteriaBuilder, itemQuery, itemRoot, filter).toArray(Predicate[]::new));
    itemQuery.orderBy(ordering(criteriaBuilder, itemQuery, itemRoot, filter));

    CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
    Root<BuildDbEntity> countRoot = countQuery.from(BuildDbEntity.class);
    countQuery
        .select(criteriaBuilder.count(countRoot))
        .where(filters(criteriaBuilder, countQuery, countRoot, filter).toArray(Predicate[]::new));

    var items =
        em.createQuery(itemQuery)
            .setFirstResult(filter.page() * filter.size())
            .setMaxResults(filter.size())
            .getResultList()
            .stream()
            .map(mapper::toDomain)
            .toList();
    return new Page(items, em.createQuery(countQuery).getSingleResult());
  }

  private List<Predicate> filters(
      CriteriaBuilder criteriaBuilder, CriteriaQuery<?> query, Root<BuildDbEntity> build, Filter filter) {
    List<Predicate> predicates = new ArrayList<>();
    if (filter.gameVersionId() != null) {
      predicates.add(criteriaBuilder.equal(build.get("gameVersionId"), filter.gameVersionId()));
    }
    if (filter.search() != null && !filter.search().isBlank()) {
      String search = filter.search().toLowerCase(Locale.ROOT);
      predicates.add(
          criteriaBuilder.greaterThan(
              criteriaBuilder.locate(criteriaBuilder.lower(build.get("title")), search), 0));
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

  private List<Order> ordering(
      CriteriaBuilder criteriaBuilder,
      CriteriaQuery<BuildDbEntity> query,
      Root<BuildDbEntity> build,
      Filter filter) {
    List<Order> ordering = new ArrayList<>();
    if ("rated".equals(filter.sort())) {
      ordering.add(criteriaBuilder.desc(wilsonLowerBound(criteriaBuilder, query, build)));
    }
    if ("score".equals(filter.sort()) || "rated".equals(filter.sort())) {
      ordering.add(criteriaBuilder.desc(score(criteriaBuilder, query, build)));
    }
    ordering.add(criteriaBuilder.desc(build.get("createdAt")));
    ordering.add(criteriaBuilder.asc(build.get("id")));
    return ordering;
  }

  private Subquery<Long> score(
      CriteriaBuilder criteriaBuilder,
      CriteriaQuery<BuildDbEntity> query,
      Root<BuildDbEntity> build) {
    Subquery<Long> score = query.subquery(Long.class);
    Root<VoteDbEntity> vote = score.from(VoteDbEntity.class);
    score
        .select(
            criteriaBuilder.coalesce(
                criteriaBuilder.sum(criteriaBuilder.toLong(vote.<Number>get("value"))), 0L))
        .where(criteriaBuilder.equal(vote.get("buildId"), build.get("id")));
    return score;
  }

  /** SQL expression for the Wilson lower bound at approximately 95% confidence. */
  private Subquery<Double> wilsonLowerBound(
      CriteriaBuilder cb, CriteriaQuery<BuildDbEntity> query, Root<BuildDbEntity> build) {
    Subquery<Double> ranking = query.subquery(Double.class);
    Root<VoteDbEntity> vote = ranking.from(VoteDbEntity.class);
    var count = cb.count(vote);
    var up = cb.coalesce(
        cb.sum(cb.<Double>selectCase().when(cb.equal(vote.get("value"), 1), 1.0)
            .otherwise(0.0)), 0.0);
    // Keep every denominator nonzero, including for an empty aggregate.
    var n = cb.<Double>selectCase().when(cb.equal(count, 0L), 1.0)
        .otherwise(cb.toDouble(count));
    var p = cb.quot(up, n);
    double z = 1.96;
    double zSquared = z * z;
    var variance = cb.sum(
        cb.quot(cb.prod(p, cb.diff(1.0, p)), n),
        cb.quot(zSquared, cb.prod(4.0, cb.prod(n, n))));
    var numerator = cb.diff(
        cb.sum(p, cb.quot(zSquared, cb.prod(2.0, n))),
        cb.prod(z, cb.sqrt(variance)));
    var lowerBound = cb.quot(numerator, cb.sum(1.0, cb.quot(zSquared, n)));
    ranking.select(cb.<Double>selectCase().when(cb.equal(up, 0.0), 0.0)
            .otherwise(cb.toDouble(lowerBound)))
        .where(cb.equal(vote.get("buildId"), build.get("id")));
    return ranking;
  }

  public void save(Build build) {
    em.merge(mapper.toEntity(build));
  }

  public void delete(UUID id) {
    em.remove(em.find(BuildDbEntity.class, id));
  }
}
