package dev.ringlab.adapter.out.db.auth;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "external_identities")
@IdClass(ExternalIdentityDbEntity.Key.class)
public class ExternalIdentityDbEntity {
  @Id @Column(length = 30) public String provider;
  @Id @Column(name = "provider_subject", length = 255) public String subject;
  @Column(name = "user_id", nullable = false) public UUID userId;
  @Column(name = "created_at", nullable = false) public Instant createdAt;

  public static class Key implements Serializable {
    public String provider;
    public String subject;

    public Key() {}

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(provider, key.provider) && Objects.equals(subject, key.subject);
    }

    @Override
    public int hashCode() { return Objects.hash(provider, subject); }
  }
}
