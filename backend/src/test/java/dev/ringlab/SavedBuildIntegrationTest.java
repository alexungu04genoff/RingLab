package dev.ringlab;

import dev.ringlab.port.out.SavedBuildRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SavedBuildIntegrationTest {
  @Inject EntityManager em;
  @Inject SavedBuildRepository saved;

  UUID user() {
    var id = UUID.randomUUID();
    em.createNativeQuery("insert into users (id,username,email,password_hash,created_at,email_verified_at) values (:id,:name,:email,'test',now(),now())")
        .setParameter("id",id).setParameter("name","s"+id.toString().substring(0,20))
        .setParameter("email",id+"@example.test").executeUpdate();
    return id;
  }
  UUID build(UUID author) {
    var id = UUID.randomUUID();
    em.createNativeQuery("insert into builds (id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,created_at,updated_at) "
        + "select :id,'Saved literal %_','',:author,(select id from racers limit 1),"
        + "(select id from machine_parts where part_type='FRONT' limit 1),(select id from machine_parts where part_type='REAR' limit 1),"
        + "(select id from machine_parts where part_type='TIRE' limit 1),now(),now()")
        .setParameter("id",id).setParameter("author",author).executeUpdate();
    return id;
  }
  @Test
  void concurrentSaveChronologyFilteringAndCascades() throws Exception {
    UUID[] ids = QuarkusTransaction.requiringNew().call(() -> { var a=user(); var b=user(); return new UUID[]{a,b,build(a),build(a)}; });
    var a=ids[0]; var b=ids[1]; var build=ids[2]; var second=ids[3];
    try (var pool=Executors.newFixedThreadPool(4)) {
      var calls = new ArrayList<Future<Instant>>();
      for (int i=0;i<4;i++) calls.add(pool.submit(() -> QuarkusTransaction.requiringNew().call(() -> saved.save(a,build))));
      var first=calls.getFirst().get();
      for (var call:calls) assertEquals(first,call.get());
      QuarkusTransaction.requiringNew().run(() -> {
        assertEquals(1,saved.list(a,null,null,0,12).total());
        assertEquals(0,saved.list(b,null,null,0,12).total());
        var statistics = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
          long before = statistics.getPrepareStatementCount();
          assertEquals(Set.of(build),saved.status(a,Set.of(build,UUID.randomUUID())));
          assertEquals(1,statistics.getPrepareStatementCount()-before);
          before = statistics.getPrepareStatementCount();
          assertEquals(Set.of(),saved.status(a,Set.of()));
          assertEquals(before,statistics.getPrepareStatementCount());
        } finally { statistics.setStatisticsEnabled(enabled); }
        saved.save(a,second);
        assertEquals(second,saved.list(a,null,null,0,1).items().getFirst().buildId());
        assertEquals(build,saved.list(a,null,null,1,1).items().getFirst().buildId());
        assertEquals(2,saved.list(a,"%_",null,0,12).total());
        assertEquals(0,saved.list(a,"absent",null,0,12).total());
        assertEquals(0,saved.list(a,null,UUID.randomUUID(),0,12).total());
        var patch = (UUID) em.createNativeQuery("select id from game_versions limit 1").getSingleResult();
        em.createNativeQuery("update builds set game_version_id=:patch where id=:id").setParameter("patch",patch).setParameter("id",build).executeUpdate();
        assertEquals(1,saved.list(a,null,patch,0,12).total());
        var racerName = (String) em.createNativeQuery("select r.name from racers r join builds b on b.racer_id=r.id where b.id=:id").setParameter("id",build).getSingleResult();
        assertEquals(2,saved.list(a,racerName.toUpperCase(),null,0,12).total());
        em.createNativeQuery("update saved_builds set saved_at=:time where user_id=:user").setParameter("time",first).setParameter("user",a).executeUpdate();
        em.clear();
        assertEquals(List.of(build,second).stream().sorted(Comparator.comparing(UUID::toString)).toList(),
            saved.list(a,null,null,0,12).items().stream().map(SavedBuildRepository.Bookmark::buildId).toList());
        em.createNativeQuery("update builds set title='Live title' where id=:id").setParameter("id",build).executeUpdate();
        assertEquals(first,saved.list(a,"Live title",null,0,12).items().getFirst().savedAt());
        saved.remove(b,build);
        assertEquals(first,saved.save(a,build));
        saved.remove(a,build); saved.remove(a,build);
        assertTrue(saved.save(a,build).isAfter(first));
        saved.save(b,build);
        em.createNativeQuery("delete from users where id=:id").setParameter("id",b).executeUpdate();
        assertEquals(Set.of(),saved.status(b,Set.of(build)));
        em.createNativeQuery("delete from builds where id=:id").setParameter("id",build).executeUpdate();
        assertEquals(Set.of(),saved.status(a,Set.of(build)));
        saved.remove(a,build);
      });
      assertThrows(dev.ringlab.application.NotFoundException.class,
          () -> QuarkusTransaction.requiringNew().run(() -> saved.save(a,build)));
    } finally {
      QuarkusTransaction.requiringNew().run(() -> {
        em.createNativeQuery("delete from builds where author_id=:id").setParameter("id",a).executeUpdate();
        em.createNativeQuery("delete from users where id in (:a,:b)").setParameter("a",a).setParameter("b",b).executeUpdate();
      });
    }
  }
}
