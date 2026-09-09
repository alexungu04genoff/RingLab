package dev.ringlab.gamedata.application.port.out;

import dev.ringlab.gamedata.domain.GameItem;
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
