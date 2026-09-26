package dev.ringlab.adapter.out.db.build;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "saved_builds")
@IdClass(SavedBuildDbEntity.Key.class)
public class SavedBuildDbEntity {
  @Id @Column(name = "user_id") public UUID userId;
  @Id @Column(name = "build_id") public UUID buildId;
  @Column(name = "saved_at", nullable = false) public Instant savedAt;

  public static class Key implements Serializable {
    public UUID userId;
    public UUID buildId;
    public Key() {}
    @Override public boolean equals(Object other) {
      return other instanceof Key key && Objects.equals(userId, key.userId) && Objects.equals(buildId, key.buildId);
    }
    @Override public int hashCode() { return Objects.hash(userId, buildId); }
  }
}
