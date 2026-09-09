package dev.ringlab.application.gamedata.port.out;

import dev.ringlab.domain.gamedata.GameItem;
import java.util.*;

public interface GameDataStore {
  enum Kind {
    RACER,
    MACHINE,
    GADGET
  }

  List<GameItem> list(Kind kind);

  Optional<GameItem> find(Kind kind, UUID id);
}
