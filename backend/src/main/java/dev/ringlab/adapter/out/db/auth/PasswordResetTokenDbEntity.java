package dev.ringlab.adapter.out.db.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenDbEntity {
  @Id @Column(name = "user_id") public UUID userId;
  @Column(name = "token_hash", nullable = false, unique = true, length = 64) public String tokenHash;
  @Column(name = "created_at", nullable = false) public Instant createdAt;
  @Column(name = "expires_at", nullable = false) public Instant expiresAt;
}
