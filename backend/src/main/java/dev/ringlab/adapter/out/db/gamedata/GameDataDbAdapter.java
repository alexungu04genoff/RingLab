package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.application.gamedata.port.out.GameDataStore;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.Racer;
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

  public List<Racer> listRacers() {
    return em
        .createQuery("from RacerDbEntity order by name", RacerDbEntity.class)
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  public Optional<Racer> findRacer(UUID id) {
    return Optional.ofNullable(em.find(RacerDbEntity.class, id)).map(mapper::toDomain);
  }

  public List<Machine> listMachines() {
    return em
        .createQuery("from MachineDbEntity order by name", MachineDbEntity.class)
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  public Optional<Machine> findMachine(UUID id) {
    return Optional.ofNullable(em.find(MachineDbEntity.class, id)).map(mapper::toDomain);
  }

  public List<Gadget> listGadgets() {
    return em
        .createQuery("from GadgetDbEntity order by name", GadgetDbEntity.class)
        .getResultList()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  public Optional<Gadget> findGadget(UUID id) {
    return Optional.ofNullable(em.find(GadgetDbEntity.class, id)).map(mapper::toDomain);
  }
}
