package dev.ringlab.build.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "builds")
public class BuildEntity {
  @Id public UUID id;

  @Column(nullable = false, length = 120)
  public String title;

  @Column(nullable = false, columnDefinition = "text")
  public String description;

  @Column(name = "author_id", nullable = false)
  public UUID authorId;

  @Column(name = "racer_id", nullable = false)
  public UUID racerId;

  @Column(name = "machine_id", nullable = false)
  public UUID machineId;

  @ElementCollection
  @CollectionTable(name = "build_gadgets", joinColumns = @JoinColumn(name = "build_id"))
  @Column(name = "gadget_id", nullable = false)
  @OrderColumn(name = "position")
  public List<UUID> gadgetIds = new ArrayList<>();

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
