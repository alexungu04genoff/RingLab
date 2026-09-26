package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.RaceMap;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "race_maps")
public class RaceMapDbEntity {
  @Id public UUID id;
  @Column(nullable = false, length = 120) public String name;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24) public RaceMap.Category category;
  @Column(name = "content_pack", length = 120) public String contentPack;
  @Column(name = "image_path") public String imagePath;
  @Column(name = "catalog_order", nullable = false) public int catalogOrder;

  public RaceMap toDomain() {
    return new RaceMap(id, name, category, contentPack, imagePath, catalogOrder);
  }
}
