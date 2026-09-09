package dev.ringlab.gamedata.adapter.out.persistence;

import dev.ringlab.gamedata.domain.GameItem;
import org.mapstruct.*;

@Mapper(componentModel = "jakarta-cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GameDataMapper {
  GameItem toDomain(RacerEntity entity);

  GameItem toDomain(MachineEntity entity);

  GameItem toDomain(GadgetEntity entity);
}
