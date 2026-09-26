package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record RaceMap(UUID id, String name, Category category, String contentPack,
                      String imagePath, int catalogOrder) {
  public enum Category { MAIN_COURSE, CROSSWORLD }
}
