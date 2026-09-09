package dev.ringlab.adapter.out.db.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.out.db.vote.VoteDbEntity;
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
    itemQuery.where(filters(criteriaBuilder, itemRoot, filter).toArray(Predicate[]::new));
    itemQuery.orderBy(ordering(criteriaBuilder, itemQuery, itemRoot, filter));

    CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
    Root<BuildDbEntity> countRoot = countQuery.from(BuildDbEntity.class);
    countQuery
        .select(criteriaBuilder.count(countRoot))
        .where(filters(criteriaBuilder, countRoot, filter).toArray(Predicate[]::new));

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
      CriteriaBuilder criteriaBuilder, Root<BuildDbEntity> build, Filter filter) {
    List<Predicate> predicates = new ArrayList<>();
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
      predicates.add(criteriaBuilder.equal(build.get("machineId"), filter.machineId()));
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
    if ("score".equals(filter.sort())) {
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

  public void save(Build build) {
    em.merge(mapper.toEntity(build));
  }

  public void delete(UUID id) {
    em.remove(em.find(BuildDbEntity.class, id));
  }
}
