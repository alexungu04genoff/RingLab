package dev.ringlab.adapter.out.db.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserDbEntity {
  @Id public UUID id;

  @Column(nullable = false, unique = true, length = 30)
  public String username;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  public String passwordHash;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
