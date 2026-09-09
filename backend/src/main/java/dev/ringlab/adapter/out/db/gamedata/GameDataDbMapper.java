package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.Racer;
import org.mapstruct.*;

@Mapper(componentModel = "jakarta-cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GameDataDbMapper {
  Racer toDomain(RacerDbEntity entity);

  Machine toDomain(MachineDbEntity entity);

  Gadget toDomain(GadgetDbEntity entity);
}
