package demo.mesharena.stadium;

import demo.mesharena.common.Commons;
import demo.mesharena.common.Point;
import demo.mesharena.common.Segment;
import demo.mesharena.common.TracingContext;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.opentracing.propagation.Format.Builtin;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.PrometheusScrapingHandler;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;

import static demo.mesharena.common.Commons.*;

public class Stadium extends AbstractVerticle {

  private static final Optional<Tracer> TRACER = getTracer("stadium");
  private static final String LOCALS = Commons.getStringEnv("STADIUM_LOCALS", "Locals");
  private static final String VISITORS = Commons.getStringEnv("STADIUM_VISITORS", "Visitors");
  private static final String NAME = Commons.getStringEnv("STADIUM_NAME", "stadium");
  private static final double SCALE = Commons.getDoubleEnv("STADIUM_SCALE", 1.0);
  private static final int TX_TOP = Commons.getIntEnv("STADIUM_TOP", 50);
  private static final int TX_LEFT = Commons.getIntEnv("STADIUM_LEFT", 20);
  private static final int TOP = TX_TOP + (int)(47 * SCALE);
  private static final int LEFT = TX_LEFT + (int)(63 * SCALE);
  private static final int TX_WIDTH = (int)(490 * SCALE);
  private static final int TX_HEIGHT = (int)(700 * SCALE);
  private static final int WIDTH = (int)(363 * SCALE);
  private static final int HEIGHT = (int)(605 * SCALE);
  private static final int GOAL_SIZE = (int)(54 * SCALE);
  private static final int MATCH_TIME = 1000 * Commons.getIntEnv("STADIUM_MATCH_TIME", 60*2);
  private static final Segment GOAL_A = new Segment(new Point(LEFT + WIDTH / 2 - GOAL_SIZE / 2, TOP), new Point(LEFT + WIDTH / 2 + GOAL_SIZE / 2, TOP));
  private static final Segment GOAL_B = new Segment(new Point(LEFT + WIDTH / 2 - GOAL_SIZE / 2, TOP + HEIGHT), new Point(LEFT + WIDTH / 2 + GOAL_SIZE / 2, TOP + HEIGHT));
  private static final Segment[] ARENA_SEGMENTS = {
      new Segment(new Point(LEFT, TOP), new Point(LEFT+WIDTH, TOP)),
      new Segment(new Point(LEFT+WIDTH, TOP), new Point(LEFT+WIDTH, TOP+HEIGHT)),
      new Segment(new Point(LEFT+WIDTH, TOP+HEIGHT), new Point(LEFT, TOP+HEIGHT)),
      new Segment(new Point(LEFT, TOP+HEIGHT), new Point(LEFT, TOP))
  };

  private final Random rnd = new SecureRandom();
  private final WebClient client;
  private final JsonObject stadiumJson;
  private final JsonObject scoreJson;

  private int scoreA = 0;
  private int scoreB = 0;
  private long startTime = System.currentTimeMillis();

  private Stadium(Vertx vertx) {
    client = WebClient.create(vertx);
    stadiumJson = new JsonObject()
        .put("id", NAME + "-stadium")
        .put("style", "position: absolute; top: " + TX_TOP + "px; left: " + TX_LEFT + "px; width: " + TX_WIDTH + "px; height: " + TX_HEIGHT + "px; background-image: url(./football-ground.png); background-size: cover; color: black")
        .put("text", "");

    scoreJson = new JsonObject()
        .put("id", NAME + "-score")
        .put("style", "position: absolute; top: " + (TX_TOP + 5) + "px; left: " + (TX_LEFT + 5) + "px; color: black; font-weight: bold; z-index: 10;")
        .put("text", "");
  }

  public static void main(String[] args) {
    Vertx vertx = Commons.vertx();
    vertx.deployVerticle(new Stadium(vertx));
  }

  @Override
  public void start() {
    // Register stadium API
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(Commons.STADIUM_PORT);
    Router router = Router.router(vertx);

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create());
    }

    router.get("/health").handler(ctx -> ctx.response().end());
    router.get("/centerBall").handler(this::startGame);
    router.get("/randomBall").handler(this::randomBall);
    router.post("/bounce").handler(this::bounce);
    router.get("/info").handler(this::info);
    vertx.createHttpServer().requestHandler(router)
        .listen(serverOptions.getPort(), serverOptions.getHost());

    // Ping-display
    vertx.setPeriodic(2000, loopId -> this.display());
  }

  private void startGame(RoutingContext ctx) {
    scoreA = scoreB = 0;
    startTime = System.currentTimeMillis();
    vertx.setPeriodic(1000, loopId -> {
      if (System.currentTimeMillis() - startTime >= MATCH_TIME) {
        // End of game!
        vertx.cancelTimer(loopId);
      }
      display();
    });
    resetBall(ctx);
    ctx.response().end();
  }

  private void randomBall(RoutingContext ctx) {
    JsonObject json = new JsonObject()
        .put("x", LEFT + rnd.nextInt(WIDTH))
        .put("y", TOP + rnd.nextInt(HEIGHT));

    HttpRequest<Buffer> request = client.put(BALL_PORT, BALL_HOST, "/setPosition");
    TRACER.ifPresent(tracer -> tracer.inject(TracingHandler.serverSpanContext(ctx), Builtin.HTTP_HEADERS, new TracingContext(request.headers())));
    request.sendJson(json, ar -> {
      if (!ar.succeeded()) {
        ar.cause().printStackTrace();
      }
    });
    ctx.response().end();
  }

  private void bounce(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      JsonObject result = bounce(ctx, new Segment(
          new Point(json.getDouble("xStart"), json.getDouble("yStart")),
          new Point(json.getDouble("xEnd"), json.getDouble("yEnd"))), -1);
      ctx.response().end(result.toString());
    });
  }

  private void resetBall(RoutingContext ctx) {
    JsonObject json = new JsonObject()
        .put("x", LEFT + WIDTH / 2)
        .put("y", TOP + HEIGHT / 2);

    HttpRequest<Buffer> request = client.put(BALL_PORT, BALL_HOST, "/setPosition");
    TRACER.ifPresent(tracer -> tracer.inject(TracingHandler.serverSpanContext(ctx), Builtin.HTTP_HEADERS, new TracingContext(request.headers())));
    request.sendJson(json, ar -> {
      if (!ar.succeeded()) {
        ar.cause().printStackTrace();
      }
    });
  }

  private JsonObject bounce(RoutingContext ctx, Segment segment, int excludeWall) {
    if (isOutside(segment.start())) {
      return new JsonObject();
    }
    Point goalA = GOAL_A.getCrossingPoint(segment);
    if (goalA != null) {
      // Team B scored!
      scoreB++;
      resetBall(ctx);
      return new JsonObject().put("scored", "visitors");
    }
    Point goalB = GOAL_B.getCrossingPoint(segment);
    if (goalB != null) {
      // Team A scored!
      scoreA++;
      resetBall(ctx);
      return new JsonObject().put("scored", "locals");
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
      JsonObject recResult = bounce(ctx, resultVector, bounceWall);
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

  private boolean isOutside(Point pos) {
    return pos.x() < LEFT || pos.x() > LEFT + WIDTH
        || pos.y() < TOP || pos.y() > TOP + HEIGHT;
  }

  private void info(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject input = buf.toJsonObject();
      boolean isVisitors = input.getBoolean("isVisitors");
      Point oppGoal = (isVisitors ? GOAL_A : GOAL_B).middle();
      Point ownGoal = (isVisitors ? GOAL_B : GOAL_A).middle();
      Point direction = oppGoal.diff(ownGoal).normalize();
      Segment defendZone = getDefendZone(ownGoal, direction);
      JsonObject output = new JsonObject()
        .put("goalX", oppGoal.x())
        .put("goalY", oppGoal.y())
        .put("scoreA", scoreA)
        .put("scoreB", scoreB)
        .put("defendZoneTop", defendZone.start().y())
        .put("defendZoneBottom", defendZone.end().y())
        .put("defendZoneLeft", defendZone.start().x())
        .put("defendZoneRight", defendZone.end().x());
      ctx.response().end(output.toString());
    });
  }

  private static Segment getDefendZone(Point goalMiddle, Point txUnitVec) {
    double boxHalfSize = Math.min(WIDTH, HEIGHT) / 4;
    Point topLeft = new Point(-boxHalfSize, -boxHalfSize);
    Point bottomRight = new Point(boxHalfSize, boxHalfSize);
    return new Segment(topLeft, bottomRight)
        .add(goalMiddle)
        .add(txUnitVec.mult(boxHalfSize));
  }

  private String getScoreText() {
    int elapsed = Math.min(MATCH_TIME, (int) (System.currentTimeMillis() - startTime) / 1000);
    int minutes = elapsed / 60;
    int seconds = elapsed % 60;
    String text = LOCALS + ": " + scoreA + " - " + VISITORS + ": " + scoreB + " ~~ Time: " + adjust(minutes) + ":" + adjust(seconds);
    if (elapsed == MATCH_TIME) {
      if (scoreA == scoreB) {
        return text + " ~~ Draw game!";
      }
      return text + " ~~ " + (scoreA > scoreB ? LOCALS : VISITORS) + " win!";
    }
    return text;
  }

  private static String adjust(int number) {
    return number < 10 ? "0" + number : String.valueOf(number);
  }

  private void display() {
    // Stadium
    client.post(UI_PORT, UI_HOST, "/display").sendJson(stadiumJson, ar -> {
      if (!ar.succeeded()) {
        ar.cause().printStackTrace();
      }
    });

    // Score
    scoreJson.put("text", NAME + " - " + getScoreText());
    client.post(UI_PORT, UI_HOST, "/display").sendJson(scoreJson, ar -> {
      if (!ar.succeeded()) {
        ar.cause().printStackTrace();
      }
    });
  }
}
