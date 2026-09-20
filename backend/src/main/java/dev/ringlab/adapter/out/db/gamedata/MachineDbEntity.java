package dev.ringlab.adapter.out.db.gamedata;

import jakarta.persistence.*;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.domain.gamedata.MachineFamily;
import java.util.UUID;

@Entity
@Table(name = "machines")
public class MachineDbEntity {
  @Id public UUID id;

  @Column(nullable = false, unique = true, length = 100)
  public String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "racing_type", length = 20)
  public RacingType racingType;

  @Column(name = "image_path")
  public String imagePath;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public MachineFamily family;
}
