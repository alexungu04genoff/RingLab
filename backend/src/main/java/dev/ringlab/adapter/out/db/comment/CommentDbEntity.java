package dev.ringlab.adapter.out.db.comment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class CommentDbEntity {
  @Id public UUID id;

  @Column(name = "build_id", nullable = false)
  public UUID buildId;

  @Column(name = "author_id", nullable = false)
  public UUID authorId;

  @Column(nullable = false, length = 2000)
  public String text;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
