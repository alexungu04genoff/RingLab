package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PassiveStatsIntegrationTest {
  @Inject GameDataRepository game;
  @Inject EntityManager em;

  private Map<String,Object> selection() {
    var machine=game.listMachines().stream().filter(m->m.name().equals("Speedster Lightning")).findFirst().orElseThrow();
    var racer=game.listRacers().stream().filter(r->r.name().equals("Amy Rose")).findFirst().orElseThrow();
    var result=new HashMap<String,Object>();
    result.put("gameVersionId",game.listGameVersions().stream().filter(v->v.version().equals("1.4.1")).findFirst().orElseThrow().id());
    result.put("racerId",racer.id());
    for(var part:game.listMachineParts()) if(part.sourceMachineId().equals(machine.id()))
      result.put(part.type().name().toLowerCase()+"PartId",part.id());
    return result;
  }

  @Test void additiveDraftContractUsesVerifiedRulesAndValidatesInputs() {
    var selection=selection();
    given().queryParams(selection).get("/api/stats/build").then().statusCode(200)
        .body("speed",equalTo(65)).body("passive",nullValue());
    given().queryParams(selection).queryParam("gadgetId",PassiveGadgetRules.id(52))
        .get("/api/stats/passive-build").then().statusCode(200)
        .body("speed",equalTo(65)).body("passive.base.speed",equalTo(65))
        .body("passive.adjustments.speed",equalTo(20)).body("passive.adjustments.acceleration",equalTo(-4))
        .body("passive.adjusted.speed",equalTo(85)).body("passive.adjusted.acceleration",equalTo(26))
        .body("passive.coverage",equalTo("CALCULATED"));
    given().queryParams(selection).queryParam("gadgetId",List.of(PassiveGadgetRules.id(52),PassiveGadgetRules.id(53)))
        .get("/api/stats/passive-build").then().statusCode(200).body("passive.adjusted.speed",equalTo(105));
    given().queryParams(selection).queryParam("gadgetId",UUID.randomUUID())
        .get("/api/stats/passive-build").then().statusCode(404);
    given().queryParams(selection).queryParam("gadgetId",List.of(PassiveGadgetRules.id(52),PassiveGadgetRules.id(52)))
        .get("/api/stats/passive-build").then().statusCode(400);
    given().queryParams(selection).queryParam("gadgetId",List.of(PassiveGadgetRules.id(49),PassiveGadgetRules.id(50),PassiveGadgetRules.id(23)))
        .get("/api/stats/passive-build").then().statusCode(400);
    selection.put("gameVersionId",game.listGameVersions().stream().filter(v->v.version().equals("1.3.1")).findFirst().orElseThrow().id());
    given().queryParams(selection).queryParam("gadgetId",PassiveGadgetRules.id(52))
        .get("/api/stats/passive-build").then().statusCode(200)
        .body("speed",equalTo(65)).body("passive.coverage",equalTo("UNSUPPORTED_VERSION"));
    given().get("/api/stats/gadget-rules").then().statusCode(200)
        .body("supportedVersion",equalTo("1.4.1")).body("gadgets.size()",equalTo(117));
  }

  @Test void persistedPagePrivateSavedAndMapEditsShareTheSameResult() {
    var token=VerifiedUserFixture.createToken(em);
    var draft=selection();String title="Passive fixture "+UUID.randomUUID();
    draft.put("title",title);draft.put("description","Isolated calculation check");
    draft.put("gadgetIds",List.of(PassiveGadgetRules.id(52)));
    String id=given().auth().oauth2(token).contentType("application/json").body(draft)
        .post("/api/builds").then().statusCode(200).extract().path("id");
    try {
      var expected=given().get("/api/stats/persisted/"+id).then().statusCode(200).extract().jsonPath().getMap("$");
      var page=given().queryParam("search",title).queryParam("includeStats",true)
          .get("/api/builds").then().statusCode(200).extract().jsonPath();
      assertEquals(expected,page.getMap("statsByBuildId.'"+id+"'"));
      given().auth().oauth2(token).put("/api/saved-builds/"+id).then().statusCode(200);
      var saved=given().auth().oauth2(token).get("/api/saved-builds").then().statusCode(200)
          .header("Cache-Control",containsString("no-store")).extract().jsonPath();
      assertEquals(expected,saved.getMap("statsByBuildId.'"+id+"'"));
      draft.put("recommendedMapIds",List.of(game.listRaceMaps().getFirst().id()));
      given().auth().oauth2(token).contentType("application/json").body(draft).put("/api/builds/"+id).then().statusCode(200);
      assertEquals(expected,given().get("/api/stats/persisted/"+id).jsonPath().getMap("$"));
      draft.put("gadgetIds",List.of(PassiveGadgetRules.id(50)));
      given().auth().oauth2(token).contentType("application/json").body(draft).put("/api/builds/"+id).then().statusCode(200);
      given().get("/api/stats/persisted/"+id).then().statusCode(200)
          .body("passive.adjustments.speed",equalTo(0)).body("passive.adjustments.handling",equalTo(3));
    } finally {given().auth().oauth2(token).delete("/api/builds/"+id).then().statusCode(204);}
    given().get("/api/stats/persisted/"+id).then().statusCode(404);
  }
}
