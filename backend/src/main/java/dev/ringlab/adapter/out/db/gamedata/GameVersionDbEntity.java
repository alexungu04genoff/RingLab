package dev.ringlab.adapter.out.db.gamedata;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "game_versions")
public class GameVersionDbEntity {
  @Id public UUID id;

  @Column(nullable = false, unique = true, length = 32)
  public String version;

  @Column(name = "released_at", nullable = false)
  public LocalDate releasedAt;
}
