package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Full HTTP requests, real JWTs, Flyway schema, and a real PostgreSQL database. */
public abstract class ApiContract {
  record Account(String token, String id, String username, String email) {}

  private RequestSpecification request(String token) {
    var request = given().contentType("application/json");
    return token == null ? request : request.auth().oauth2(token);
  }

  private Account register() {
    String name = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    String email = name + "@example.com";
    var r =
        request(null)
            .body(Map.of("username", name, "email", email, "password", "password-123"))
            .post("/api/auth/register")
            .then()
            .statusCode(200)
            .body("user.username", equalTo(name))
            .body("user.passwordHash", nullValue())
            .extract()
            .response();
    return new Account(r.path("token"), r.path("user.id"), name, email);
  }

  private List<String> ids(String collection) {
    return given().get("/api/" + collection).then().statusCode(200).extract().path("id");
  }

  private Map<String, Object> draft() {
    var gadgets = ids("gadgets");
    return new HashMap<>(
        Map.of(
            "title",
            "My race setup",
            "description",
            "A test combination",
            "racerId",
            ids("racers").getFirst(),
            "machineId",
            ids("machines").getLast(),
            "gadgetIds",
            List.of(gadgets.get(3), gadgets.get(1))));
  }

  private String create(Account author) {
    return request(author.token)
        .body(draft())
        .post("/api/builds")
        .then()
        .statusCode(200)
        .extract()
        .path("id");
  }

  @Test
  void registrationLoginAndCurrentUser() {
    var user = register();
    request(null)
        .body(
            Map.of("username", user.username.toUpperCase(Locale.ROOT), "password", "password-123"))
        .post("/api/auth/login")
        .then()
        .statusCode(200)
        .body("user.id", equalTo(user.id));
    request(user.token).get("/api/auth/me").then().statusCode(200).body("id", equalTo(user.id));
    request(user.token).post("/api/auth/logout").then().statusCode(204);
    request(null).get("/api/auth/me").then().statusCode(401);
  }

  @Test
  void rejectsDuplicateAccountsAndInvalidLogins() {
    var user = register();
    request(null)
        .body(
            Map.of(
                "username",
                user.username.toUpperCase(Locale.ROOT),
                "email",
                "other@example.com",
                "password",
                "password-123"))
        .post("/api/auth/register")
        .then()
        .statusCode(409);
    request(null)
        .body(
            Map.of(
                "username",
                "another_user",
                "email",
                user.email.toUpperCase(Locale.ROOT),
                "password",
                "password-123"))
        .post("/api/auth/register")
        .then()
        .statusCode(409);
    request(null)
        .body(Map.of("username", user.username, "password", "incorrect"))
        .post("/api/auth/login")
        .then()
        .statusCode(401);
    request(null)
        .body(Map.of("username", "nonexistent_account", "password", "incorrect"))
        .post("/api/auth/login")
        .then()
        .statusCode(401);
  }

  @Test
  void publicCatalogIsExactAndPublicMutationIsRejected() {
    Map<String, Object> racer =
        given()
            .get("/api/racers")
            .then()
            .statusCode(200)
            .body("size()", equalTo(6))
            .extract()
            .path("[0]");
    Map<String, Object> machine =
        given()
            .get("/api/machines")
            .then()
            .statusCode(200)
            .body("size()", equalTo(10))
            .extract()
            .path("[0]");
    Map<String, Object> gadget =
        given()
            .get("/api/gadgets")
            .then()
            .statusCode(200)
            .body("size()", equalTo(20))
            .extract()
            .path("[0]");

    assertThat(
        racer,
        allOf(
            hasEntry("name", "Amy Rose"),
            hasEntry("racingType", "HANDLING"),
            not(hasKey("description")),
            not(hasKey("slotCost"))));
    assertThat(
        machine,
        allOf(
            hasEntry("name", "Dark Reaper"),
            hasEntry("racingType", "SPEED"),
            not(hasKey("description")),
            not(hasKey("slotCost"))));
    assertThat(
        gadget, allOf(hasKey("description"), hasKey("slotCost"), not(hasKey("racingType"))));
    request(null).body(draft()).post("/api/builds").then().statusCode(401);
  }

  @Test
  void validReferencesAndGadgetOrderPersistWithoutGuessedRestrictions() {
    var author = register();
    var body = draft();
    String id =
        request(author.token)
            .body(body)
            .post("/api/builds")
            .then()
            .statusCode(200)
            .extract()
            .path("id");
    given()
        .get("/api/builds/" + id)
        .then()
        .statusCode(200)
        .body("gadgets.id", equalTo(body.get("gadgetIds")))
        .body("author.id", equalTo(author.id));
    body.put("gadgetIds", ids("gadgets"));
    request(author.token)
        .body(body)
        .put("/api/builds/" + id)
        .then()
        .statusCode(200)
        .body("gadgets.size()", equalTo(20));
    body.put("gadgetIds", List.of());
    request(author.token)
        .body(body)
        .put("/api/builds/" + id)
        .then()
        .statusCode(200)
        .body("gadgets.size()", equalTo(0));
  }

  @Test
  void rejectsUnknownReferencesAndBlankContent() {
    var author = register();
    for (String field : List.of("racerId", "machineId", "gadgetIds")) {
      var body = draft();
      body.put(
          field,
          field.equals("gadgetIds")
              ? List.of(UUID.randomUUID().toString())
              : UUID.randomUUID().toString());
      request(author.token).body(body).post("/api/builds").then().statusCode(400);
    }
    var body = draft();
    body.put("title", "  ");
    request(author.token).body(body).post("/api/builds").then().statusCode(400);
  }

  @Test
  void onlyAuthorCanEditOrDeleteBuild() {
    var owner = register();
    var other = register();
    String id = create(owner);
    var changed = draft();
    changed.put("title", "Updated setup");
    request(other.token).body(changed).put("/api/builds/" + id).then().statusCode(403);
    request(other.token).delete("/api/builds/" + id).then().statusCode(403);
    request(null).delete("/api/builds/" + id).then().statusCode(401);
    given().get("/api/builds/" + id).then().statusCode(200).body("title", equalTo("My race setup"));
    request(owner.token)
        .body(changed)
        .put("/api/builds/" + id)
        .then()
        .statusCode(200)
        .body("title", equalTo("Updated setup"));
    request(owner.token).delete("/api/builds/" + id).then().statusCode(204);
    given().get("/api/builds/" + id).then().statusCode(404);
  }

  private Response vote(Account user, String build, int value) {
    return request(user.token).body(Map.of("value", value)).put("/api/builds/" + build + "/vote");
  }

  @Test
  void votesAreUniqueSwitchableRemovableAndSummed() {
    var owner = register();
    var voter = register();
    String id = create(owner);
    request(null)
        .body(Map.of("value", 1))
        .put("/api/builds/" + id + "/vote")
        .then()
        .statusCode(401);
    vote(voter, id, 1).then().statusCode(200).body("score", equalTo(1)).body("myVote", equalTo(1));
    vote(voter, id, 1).then().statusCode(200).body("score", equalTo(1));
    vote(voter, id, -1)
        .then()
        .statusCode(200)
        .body("score", equalTo(-1))
        .body("myVote", equalTo(-1));
    vote(owner, id, -1).then().statusCode(200).body("score", equalTo(-2));
    vote(voter, id, 0).then().statusCode(400);
    request(voter.token)
        .delete("/api/builds/" + id + "/vote")
        .then()
        .statusCode(200)
        .body("score", equalTo(-1))
        .body("myVote", equalTo(0));
    request(voter.token)
        .delete("/api/builds/" + id + "/vote")
        .then()
        .statusCode(200)
        .body("score", equalTo(-1));
    given().get("/api/builds/" + id).then().statusCode(200).body("score", equalTo(-1));
  }

  @Test
  void commentsArePublicButDeletionRequiresCommentAuthor() {
    var owner = register();
    var commenter = register();
    String id = create(owner);
    String path = "/api/builds/" + id + "/comments";
    request(null).body(Map.of("text", "Nice setup")).post(path).then().statusCode(401);
    request(commenter.token).body(Map.of("text", " ")).post(path).then().statusCode(400);
    String comment =
        request(commenter.token)
            .body(Map.of("text", "Nice setup"))
            .post(path)
            .then()
            .statusCode(200)
            .body("authorId", equalTo(commenter.id))
            .extract()
            .path("id");
    request(owner.token).delete("/api/comments/" + comment).then().statusCode(403);
    given()
        .get(path)
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("page", equalTo(0))
        .body("size", equalTo(20))
        .body("items.text", hasItem("Nice setup"));
    request(commenter.token).delete("/api/comments/" + comment).then().statusCode(204);
    given().get(path).then().statusCode(200).body("total", equalTo(0)).body("items", empty());
  }

  @Test
  void commentsExposeTotalsAndDeterministicDatabasePagination() {
    var owner = register();
    String id = create(owner);
    String path = "/api/builds/" + id + "/comments";
    var commentIds = new ArrayList<String>();

    for (int index = 1; index <= 21; index++) {
      commentIds.add(
          request(owner.token)
              .body(Map.of("text", "Comment " + index))
              .post(path)
              .then()
              .statusCode(200)
              .extract()
              .path("id"));
    }

    given()
        .queryParam("page", 0)
        .queryParam("size", 20)
        .get(path)
        .then()
        .statusCode(200)
        .body("total", equalTo(21))
        .body("page", equalTo(0))
        .body("size", equalTo(20))
        .body("items", hasSize(20))
        .body("items[0].id", equalTo(commentIds.getFirst()))
        .body("items[19].id", equalTo(commentIds.get(19)));

    given()
        .queryParam("page", 1)
        .queryParam("size", 20)
        .get(path)
        .then()
        .statusCode(200)
        .body("total", equalTo(21))
        .body("page", equalTo(1))
        .body("size", equalTo(20))
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(commentIds.getLast()));
  }

  @Test
  void filteringSortingAndPaginationUseDatabaseState() {
    var owner = register();
    String first = create(owner);
    String second = create(owner);
    vote(owner, first, 1).then().statusCode(200);
    var data = draft();
    data.put("title", "Unique Needle");
    request(owner.token).body(data).put("/api/builds/" + first).then().statusCode(200);
    given()
        .queryParam("authorId", owner.id)
        .queryParam("sort", "score")
        .queryParam("size", 1)
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(2))
        .body("items[0].id", equalTo(first));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("sort", "newest")
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("items[0].id", equalTo(second));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("search", "nEeDlE")
        .queryParam("racerId", data.get("racerId"))
        .queryParam("machineId", data.get("machineId"))
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].id", equalTo(first));
    given().queryParam("size", 1000).get("/api/builds").then().statusCode(400);
    given().queryParam("sort", "anything").get("/api/builds").then().statusCode(400);
  }

  @Test
  void buildListRetainsEveryFilterOrderingPaginationAndLiteralSearch() {
    var owner = register();
    var otherAuthor = register();
    var racers = ids("racers");
    var machines = ids("machines");

    var literalBuild = draft();
    literalBuild.put("title", "Literal %_ build");
    literalBuild.put("racerId", racers.getFirst());
    literalBuild.put("machineId", machines.getLast());
    String first =
        request(owner.token).body(literalBuild).post("/api/builds").then().statusCode(200).extract().path("id");

    var newerBuild = draft();
    newerBuild.put("title", "Other owner build");
    newerBuild.put("racerId", racers.get(1));
    newerBuild.put("machineId", machines.getFirst());
    String second =
        request(owner.token).body(newerBuild).post("/api/builds").then().statusCode(200).extract().path("id");

    var otherAuthorBuild = draft();
    otherAuthorBuild.put("title", "Other author build");
    request(otherAuthor.token)
        .body(otherAuthorBuild)
        .post("/api/builds")
        .then()
        .statusCode(200);
    vote(owner, first, 1).then().statusCode(200);

    given().queryParam("size", 50).get("/api/builds").then().statusCode(200).body("total", greaterThanOrEqualTo(3));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("search", "%_")
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].id", equalTo(first));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("racerId", racers.getFirst())
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].id", equalTo(first));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("machineId", machines.getLast())
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].id", equalTo(first));
    given()
        .queryParam("authorId", owner.id)
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(2))
        .body("items[0].id", equalTo(second));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("racerId", racers.getFirst())
        .queryParam("machineId", machines.getLast())
        .queryParam("search", "literal")
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("items[0].id", equalTo(first));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("sort", "score")
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("items[0].id", equalTo(first));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("size", 1)
        .queryParam("page", 0)
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(2))
        .body("items[0].id", equalTo(second));
    given()
        .queryParam("authorId", owner.id)
        .queryParam("size", 1)
        .queryParam("page", 1)
        .get("/api/builds")
        .then()
        .statusCode(200)
        .body("total", equalTo(2))
        .body("items[0].id", equalTo(first));
  }

  @Test
  void deletingBuildCascadesItsCommentsAndVotes() {
    var owner = register();
    String id = create(owner);
    vote(owner, id, 1).then().statusCode(200);
    String comment =
        request(owner.token)
            .body(Map.of("text", "A note"))
            .post("/api/builds/" + id + "/comments")
            .then()
            .statusCode(200)
            .extract()
            .path("id");
    request(owner.token).delete("/api/builds/" + id).then().statusCode(204);
    request(owner.token).delete("/api/comments/" + comment).then().statusCode(404);
    request(owner.token).get("/api/builds/" + id + "/vote").then().statusCode(404);
  }
}
