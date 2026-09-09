package dev.ringlab.gamedata.adapter.out.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "gadgets")
public class GadgetEntity {
  @Id public UUID id;

  @Column(nullable = false, unique = true, length = 100)
  public String name;

  @Column(columnDefinition = "text")
  public String description;

  @Column(name = "slot_cost")
  public Integer slotCost;

  @Column(name = "image_path")
  public String imagePath;
}
