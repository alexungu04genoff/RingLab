package dev.ringlab.adapter.out.db.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.out.db.gamedata.MachinePartDbEntity;
import dev.ringlab.domain.build.Build;
import dev.ringlab.port.out.BuildRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
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

  public List<Build> search(Filter filter) {
    CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();

    CriteriaQuery<BuildDbEntity> itemQuery = criteriaBuilder.createQuery(BuildDbEntity.class);
    Root<BuildDbEntity> itemRoot = itemQuery.from(BuildDbEntity.class);
    itemQuery.where(filters(criteriaBuilder, itemQuery, itemRoot, filter).toArray(Predicate[]::new));
    return em.createQuery(itemQuery).getResultList().stream().map(mapper::toDomain).toList();
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

  public void save(Build build) {
    em.merge(mapper.toEntity(build));
  }

  public void delete(UUID id) {
    em.remove(em.find(BuildDbEntity.class, id));
  }
}
