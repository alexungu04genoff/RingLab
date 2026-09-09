package dev.ringlab.vote.adapter.out.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "build_id"}))
public class VoteEntity {
  @Id public UUID id;

  @Column(name = "user_id", nullable = false)
  public UUID userId;

  @Column(name = "build_id", nullable = false)
  public UUID buildId;

  @Column(nullable = false)
  public short value;
}
