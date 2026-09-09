package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.GameItem;
import org.mapstruct.*;

@Mapper(componentModel = "jakarta-cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GameDataDbMapper {
  GameItem toDomain(RacerDbEntity entity);

  GameItem toDomain(MachineDbEntity entity);

  GameItem toDomain(GadgetDbEntity entity);
}
