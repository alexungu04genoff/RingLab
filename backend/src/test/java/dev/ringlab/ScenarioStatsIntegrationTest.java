package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.domain.gamedata.PassiveGadgetRules;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ScenarioStatsIntegrationTest {
  @Inject GameDataRepository game;
  @Inject EntityManager em;

  private Map<String,Object> selection() {
    var machine=game.listMachines().stream().filter(m->m.name().equals("Speedster Lightning")).findFirst().orElseThrow();
    var racer=game.listRacers().stream().filter(r->r.name().equals("Amy Rose")).findFirst().orElseThrow();
    var request=new HashMap<String,Object>();
    request.put("gameVersionId",game.listGameVersions().stream().filter(v->v.version().equals("1.4.1")).findFirst().orElseThrow().id());
    request.put("racerId",racer.id());
    for(var p:game.listMachineParts()) if(p.sourceMachineId().equals(machine.id())) request.put(p.type().name().toLowerCase()+"PartId",p.id());
    request.put("gadgetIds",List.of(PassiveGadgetRules.id(45)));
    request.put("scenario",Map.of("lap",1));
    return request;
  }

  @Test void publicCalculationUsesServerFactsAndNeverWritesABuild() {
    var count=em.createQuery("select count(b) from BuildDbEntity b",Long.class).getSingleResult();
    var request=selection();
    request.put("stats",Map.of("speed",9999));request.put("machineType","BOOST");
    given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(200)
        .body("passive.base.speed",equalTo(65)).body("adjustments.speed",equalTo(20))
        .body("total.speed",equalTo(85)).body("coverage",equalTo("CALCULATED"));
    request.put("scenario",Map.of("lap",2));
    given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(200)
        .body("total.speed",equalTo(65)).body("effects[0].status",equalTo("CONDITION_NOT_MET"));
    request.put("scenario",Map.of());
    given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(200)
        .body("total",nullValue()).body("effects[0].adjustment",nullValue()).body("coverage",equalTo("PARTIAL"));
    assertEquals(count,em.createQuery("select count(b) from BuildDbEntity b",Long.class).getSingleResult());
    given().get("/api/stats/scenario-rules").then().statusCode(200).body("supportedVersion",equalTo("1.4.1"));
  }

  @Test void transportAndCatalogValidationAreBoundedAndSafe() {
    var request=selection();
    for(var scenario:List.of(Map.of("lap",0),Map.of("lap",4),Map.of("ringsHeld",-1),Map.of("ringsHeld",1000),
        Map.of("distanceToFinish",50001),Map.of("ringsHeld",1.5),Map.of("vehicleForm","MAP"))) {
      request.put("scenario",scenario);
      given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(400);
    }
    request.put("scenario",Map.of());request.put("gadgetIds",Collections.nCopies(7,PassiveGadgetRules.id(45)));
    given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(400);
    request.put("gadgetIds",List.of(UUID.randomUUID()));
    given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(404)
        .body("message",equalTo("Gadget not found"));
    request.remove("scenario");
    given().contentType("application/json").body(request).post("/api/stats/scenario-build").then().statusCode(400);
  }
}
