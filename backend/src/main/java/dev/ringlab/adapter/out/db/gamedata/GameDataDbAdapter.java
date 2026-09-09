package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.application.gamedata.port.out.GameDataStore;
import dev.ringlab.domain.gamedata.GameItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.*;

@ApplicationScoped
public class GameDataDbAdapter implements GameDataStore {
  private final EntityManager em;
  private final GameDataDbMapper mapper;

  public GameDataDbAdapter(EntityManager em, GameDataDbMapper mapper) {
    this.em = em;
    this.mapper = mapper;
  }

  public List<GameItem> list(Kind kind) {
    return switch (kind) {
      case RACER ->
          em
              .createQuery("from RacerDbEntity order by name", RacerDbEntity.class)
              .getResultList()
              .stream()
              .map(mapper::toDomain)
              .toList();
      case MACHINE ->
          em
              .createQuery("from MachineDbEntity order by name", MachineDbEntity.class)
              .getResultList()
              .stream()
              .map(mapper::toDomain)
              .toList();
      case GADGET ->
          em
              .createQuery("from GadgetDbEntity order by name", GadgetDbEntity.class)
              .getResultList()
              .stream()
              .map(mapper::toDomain)
              .toList();
    };
  }

  public Optional<GameItem> find(Kind kind, UUID id) {
    return Optional.ofNullable(
        switch (kind) {
          case RACER -> mapper.toDomain(em.find(RacerDbEntity.class, id));
          case MACHINE -> mapper.toDomain(em.find(MachineDbEntity.class, id));
          case GADGET -> mapper.toDomain(em.find(GadgetDbEntity.class, id));
        });
  }
}
