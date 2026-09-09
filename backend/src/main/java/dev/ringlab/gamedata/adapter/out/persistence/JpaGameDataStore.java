package dev.ringlab.gamedata.adapter.out.persistence;

import dev.ringlab.gamedata.application.port.out.GameDataStore;
import dev.ringlab.gamedata.domain.GameItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.*;

@ApplicationScoped
public class JpaGameDataStore implements GameDataStore {
  private final EntityManager em;
  private final GameDataMapper mapper;

  public JpaGameDataStore(EntityManager em, GameDataMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  public List<GameItem> list(Kind kind) {
    return switch (kind) {
      case RACER ->
          em
              .createQuery("from RacerEntity order by name", RacerEntity.class)
              .getResultList()
              .stream()
              .map(mapper::toDomain)
              .toList();
      case MACHINE ->
          em
              .createQuery("from MachineEntity order by name", MachineEntity.class)
              .getResultList()
              .stream()
              .map(mapper::toDomain)
              .toList();
      case GADGET ->
          em
              .createQuery("from GadgetEntity order by name", GadgetEntity.class)
              .getResultList()
              .stream()
              .map(mapper::toDomain)
              .toList();
    };
  }

  public Optional<GameItem> find(Kind kind, UUID id) {
    return Optional.ofNullable(
        switch (kind) {
          case RACER -> mapper.toDomain(em.find(RacerEntity.class, id));
          case MACHINE -> mapper.toDomain(em.find(MachineEntity.class, id));
          case GADGET -> mapper.toDomain(em.find(GadgetEntity.class, id));
        });
  }
}
