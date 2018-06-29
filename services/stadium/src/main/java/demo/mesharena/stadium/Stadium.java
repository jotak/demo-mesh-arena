package demo.mesharena.stadium;

import demo.mesharena.common.Point;
import demo.mesharena.common.Segment;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static demo.mesharena.common.Commons.*;

public class Stadium extends AbstractVerticle {

  private static final int TOP = 50;
  private static final int LEFT = 20;
  private static final int WIDTH = 1000;
  private static final int HEIGHT = 700;
  private static final int GOAL_SIZE = 100;
  private static final int MATCH_TIME = 60 * 5;
  private static final Segment GOAL_A = new Segment(new Point(LEFT, TOP + HEIGHT / 2 - GOAL_SIZE / 2), new Point(LEFT, TOP + HEIGHT / 2 + GOAL_SIZE / 2));
  private static final Segment GOAL_B = new Segment(new Point(LEFT + WIDTH, TOP + HEIGHT / 2 - GOAL_SIZE / 2), new Point(LEFT + WIDTH, TOP + HEIGHT / 2 + GOAL_SIZE / 2));
  private static final Segment[] ARENA_SEGMENTS = {
      new Segment(new Point(LEFT, TOP), new Point(LEFT+WIDTH, TOP)),
      new Segment(new Point(LEFT+WIDTH, TOP), new Point(LEFT+WIDTH, TOP+HEIGHT)),
      new Segment(new Point(LEFT+WIDTH, TOP+HEIGHT), new Point(LEFT, TOP+HEIGHT)),
      new Segment(new Point(LEFT, TOP+HEIGHT), new Point(LEFT, TOP))
  };

  private final JsonObject stadiumJson;
  private final JsonObject goalsJson;
  private final JsonObject scoreJson;

  private int scoreA = 0;
  private int scoreB = 0;
  private int elapsed = 0;

  private Stadium() {
    stadiumJson = new JsonObject()
        .put("id", "stadium")
        .put("style", "position: absolute; top: " + TOP + "px; left: " + LEFT + "px; width: " + WIDTH + "px; height: " + HEIGHT + "px; border: 1px solid;")
        .put("text", "");

    int median = TOP + HEIGHT / 2;
    goalsJson = new JsonObject()
        .put("id", "goals")
        .put("style", "position: absolute; top: " + (median - GOAL_SIZE/2) + "px; left: " + LEFT + "px; width: " + (WIDTH-6) + "px; height: " + GOAL_SIZE + "px; border-left: 4px solid red; border-right: 4px solid red;")
        .put("text", "");

    scoreJson = new JsonObject()
        .put("id", "score")
        .put("style", "position: absolute;")
        .put("text", "");
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new Stadium());
  }

  @Override
  public void start() throws Exception {
    // Register stadium API
    Router router = Router.router(vertx);
    router.get("/health").handler(ctx -> ctx.response().end());
    router.get("/start").handler(this::startGame);
    router.post("/bounce").handler(this::bounce);
    router.get("/info").handler(this::info);
    vertx.createHttpServer().requestHandler(router::accept).listen(STADIUM_PORT, STADIUM_HOST);

    // First display
    display();
  }

  private void startGame(RoutingContext ctx) {
    scoreA = scoreB = elapsed = 0;
    vertx.setPeriodic(1000, loopId -> {
      if (elapsed >= MATCH_TIME) {
        // End of game!
        vertx.cancelTimer(loopId);
        display();
      } else {
        this.update();
      }
    });
    resetBall();
    ctx.response().end();
  }

  private void bounce(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      JsonObject result = bounce(new Segment(
          new Point(json.getDouble("xStart"), json.getDouble("yStart")),
          new Point(json.getDouble("xEnd"), json.getDouble("yEnd"))), -1);
      ctx.response().end(result.toString());
    });
  }

  private void resetBall() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(BALL_HOST).setDefaultPort(BALL_PORT));
    HttpClientRequest request = client.request(HttpMethod.PUT, "/setPosition", response -> {});

    String json = new JsonObject()
        .put("x", LEFT + WIDTH / 2)
        .put("y", TOP + HEIGHT / 2)
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
  }

  private JsonObject bounce(Segment segment, int excludeWall) {
    Point goalA = GOAL_A.getCrossingPoint(segment);
    if (goalA != null) {
      // Team B scored!
      scoreB++;
      resetBall();
      return new JsonObject().put("scored", "B");
    }
    Point goalB = GOAL_B.getCrossingPoint(segment);
    if (goalB != null) {
      // Team A scored!
      scoreA++;
      resetBall();
      return new JsonObject().put("scored", "B");
    }
    double minDistance = -1;
    Point collision = null;
    int bounceWall = -1;
    for (int i = 0; i < ARENA_SEGMENTS.length; i++) {
      if (i == excludeWall) {
        continue;
      }
      Segment wall = ARENA_SEGMENTS[i];
      Point tempCollision = wall.getCrossingPoint(segment);
      if (tempCollision != null) {
        double dist = tempCollision.diff(segment.start()).size();
        // minDistance is used to keep only the first wall encountered; if any wall was encounted first, forget this one.
        if (minDistance < 0 || dist < minDistance) {
          minDistance = dist;
          collision = tempCollision;
          bounceWall = i;
        }
      }
    }
    if (collision != null) {
      // Calculate bouncing vector and position of resulting position
      Point d = segment.end().diff(collision).normalize();
      Point p = ARENA_SEGMENTS[bounceWall].start().diff(collision).normalize();
      double dAngle = Math.acos(d.x());
      if (d.y() > 0) {
        dAngle = 2 * Math.PI - dAngle;
      }
      double pAngle = Math.acos(p.x());
      if (p.y() > 0) {
        pAngle = 2 * Math.PI - pAngle;
      }
      double dpAngle = 2 * pAngle - dAngle;
      while (dpAngle >= 2 * Math.PI) {
        dpAngle -= 2 * Math.PI;
      }
      Point result = collision.add(new Point(Math.cos(dpAngle), -Math.sin(dpAngle)).mult(segment.size() - minDistance));
      Segment resultVector = new Segment(collision, result);
      // Recursive call to check if the result vector itself is bouncing again
      JsonObject recResult = bounce(resultVector, bounceWall);
      if (recResult.isEmpty()) {
        // No bounce in recursive call => return new vector
        Point normalized = resultVector.derivate().normalize();
        return new JsonObject()
            .put("x", result.x())
            .put("y", result.y())
            .put("dx", normalized.x())
            .put("dy", normalized.y());
      }
      return recResult;
    }
    return new JsonObject();
  }

  private void info(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject input = buf.toJsonObject();
      boolean isVisitors = input.getBoolean("isVisitors");
      JsonObject output = new JsonObject();
      if (isVisitors) {
        output
            .put("goalX", LEFT)
            .put("goalY", TOP + HEIGHT / 2)
            .put("defendZoneTop", TOP)
            .put("defendZoneBottom", TOP + HEIGHT)
            .put("defendZoneLeft", LEFT + 2 * WIDTH / 3)
            .put("defendZoneRight", LEFT + WIDTH);
      } else {
        output
            .put("goalX", LEFT + WIDTH)
            .put("goalY", TOP + HEIGHT / 2)
            .put("defendZoneTop", TOP)
            .put("defendZoneBottom", TOP + HEIGHT)
            .put("defendZoneLeft", LEFT)
            .put("defendZoneRight", LEFT + WIDTH / 3);
      }
      ctx.response().end(output.toString());
    });
  }

  private void update() {
    elapsed++;
    display();
  }

  private String getScoreText() {
    int minutes = elapsed / 60;
    int seconds = elapsed % 60;
    String text = "Local: " + scoreA + " - Visitors: " + scoreB + " ~~ Time: " + adjust(minutes) + ":" + adjust(seconds);
    if (elapsed >= MATCH_TIME) {
      if (scoreA == scoreB) {
        return text + " ~~ Draw game!";
      }
      return text + " ~~ " + (scoreA > scoreB ? "Locals" : "Visitors") + " win!";
    }
    return text;
  }

  private static String adjust(int number) {
    return number < 10 ? "0" + number : String.valueOf(number);
  }

  private void display() {
    // Stadium
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(UI_HOST).setDefaultPort(UI_PORT));
    HttpClientRequest request = client.request(HttpMethod.POST, "/display", response -> {});

    String strJson = stadiumJson.toString();

    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(strJson.length()));
    request.write(strJson);
    request.end();

    // Goals
    request = client.request(HttpMethod.POST, "/display", response -> {});

    strJson = goalsJson.toString();

    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(strJson.length()));
    request.write(strJson);
    request.end();

    // Score
    request = client.request(HttpMethod.POST, "/display", response -> {});

    scoreJson.put("text", getScoreText());
    strJson = scoreJson.toString();

    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(strJson.length()));
    request.write(strJson);
    request.end();
  }
}
