package dev.ringlab.adapter.out.db;

import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.adapter.out.db.build.*;
import dev.ringlab.adapter.out.db.comment.*;
import dev.ringlab.adapter.out.db.vote.VoteDbAdapter;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.domain.vote.Vote;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ParentConstraintTranslationTest {
  @Test
  void translatesOnlyTheExpectedForeignKeyForEachWrite() {
    for (String constraint : List.of("votes_build_id_fkey", "comments_build_id_fkey",
        "builds_remixed_from_build_id_fkey")) {
      assertThrows(NotFoundException.class, () -> write(constraint, failure("23503", constraint)));
      for (var failure : List.of(failure("23505", constraint), failure("23503", "users_pkey"),
          failure("23503", "votes_user_id_fkey"), failure("23503", "comments_author_id_fkey"),
          failure("23503", "builds_racer_id_fkey"), failure("23503", null))) {
        assertSame(failure, assertThrows(ConstraintViolationException.class, () -> write(constraint, failure)));
      }
    }
  }

  private void write(String constraint, ConstraintViolationException failure) {
    UUID id = UUID.randomUUID();
    if (constraint.equals("comments_build_id_fkey")) {
      var adapter = new CommentDbAdapter(Mappers.getMapper(CommentDbMapper.class)) {
        @Override public void persistAndFlush(CommentDbEntity entity) { throw failure; }
      };
      adapter.create(new Comment(id, id, id, "Comment", Instant.EPOCH));
    } else if (constraint.equals("votes_build_id_fkey")) {
      new VoteDbAdapter(failingEntityManager(failure)).put(new Vote(id, id, 1));
    } else {
      new BuildDbAdapter(failingEntityManager(failure), Mappers.getMapper(BuildDbMapper.class))
          .save(new Build(id, "Build", "", id, id, id, id, id, null, id, List.of(), Instant.EPOCH, Instant.EPOCH));
    }
  }

  private EntityManager failingEntityManager(ConstraintViolationException failure) {
    Query query = (Query) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Query.class},
        (proxy, method, args) -> {
          if (method.getName().equals("setParameter")) return proxy;
          if (method.getName().equals("executeUpdate")) throw failure;
          throw new AssertionError(method.getName());
        });
    return (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {EntityManager.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "createNativeQuery" -> query;
          case "merge" -> args[0];
          case "flush" -> throw failure;
          default -> throw new AssertionError(method.getName());
        });
  }

  private ConstraintViolationException failure(String state, String constraint) {
    return new ConstraintViolationException("Private database details", new SQLException("failure", state), constraint);
  }
}
