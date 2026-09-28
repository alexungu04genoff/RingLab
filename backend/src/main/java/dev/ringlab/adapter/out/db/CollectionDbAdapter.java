package dev.ringlab.adapter.out.db;

import dev.ringlab.domain.collection.*;
import dev.ringlab.port.out.CollectionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.util.*;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class CollectionDbAdapter implements CollectionRepository {
  private final EntityManager entityManager;

  public CollectionExclusions load(UUID userId) {
    return new CollectionExclusions(ids(userId, CollectionCategory.RACER),
        ids(userId, CollectionCategory.MACHINE), ids(userId, CollectionCategory.GADGET));
  }

  private Set<UUID> ids(UUID userId, CollectionCategory category) {
    var result = new HashSet<UUID>();
    for (Object id : entityManager.createNativeQuery("SELECT item_id FROM " + table(category) + " WHERE user_id = :actor")
        .setParameter("actor", userId).getResultList()) result.add((UUID) id);
    return result;
  }

  public void setOwned(UUID userId, CollectionCategory category, UUID itemId, boolean owned) {
    // Serialize updates for an account, including opposite concurrent updates to an absent row.
    entityManager.createNativeQuery("SELECT id FROM users WHERE id = :actor FOR UPDATE")
        .setParameter("actor", userId).getSingleResult();
    String sql = owned ? "DELETE FROM " + table(category) + " WHERE user_id = :actor AND item_id = :item"
        : "INSERT INTO " + table(category) + " (user_id, item_id) VALUES (:actor, :item) ON CONFLICT DO NOTHING";
    entityManager.createNativeQuery(sql).setParameter("actor", userId).setParameter("item", itemId).executeUpdate();
  }

  private static String table(CollectionCategory category) {
    return switch (category) {
      case RACER -> "collection_racer_exclusions";
      case MACHINE -> "collection_machine_exclusions";
      case GADGET -> "collection_gadget_exclusions";
    };
  }
}
