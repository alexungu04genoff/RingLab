package dev.ringlab.gamedata.adapter.out.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "racers")
public class RacerEntity {
  @Id public UUID id;

  @Column(nullable = false, unique = true, length = 100)
  public String name;

  @Column(name = "racing_type", nullable = false, length = 20)
  public String racingType;

  @Column(name = "image_path")
  public String imagePath;
}
