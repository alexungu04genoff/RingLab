package dev.ringlab;

import static io.restassured.RestAssured.given;
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
    given().get("/api/racers").then().statusCode(200).body("size()", equalTo(6));
    given().get("/api/machines").then().statusCode(200).body("size()", equalTo(10));
    given()
        .get("/api/gadgets")
        .then()
        .statusCode(200)
        .body("size()", equalTo(20))
        .body("slotCost", everyItem(nullValue()))
        .body("description", everyItem(nullValue()));
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
    given().get(path).then().statusCode(200).body("text", hasItem("Nice setup"));
    request(commenter.token).delete("/api/comments/" + comment).then().statusCode(204);
    given().get(path).then().statusCode(200).body("size()", equalTo(0));
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
