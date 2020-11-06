package demo.mesharena.ai;

import demo.mesharena.common.Commons;
import demo.mesharena.common.DisplayMessager;
import demo.mesharena.common.Point;
import demo.mesharena.common.Segment;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.tracing.opentracing.OpenTracingUtil;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static demo.mesharena.common.Commons.BALL_HOST;
import static demo.mesharena.common.Commons.BALL_PORT;
import static demo.mesharena.common.Commons.STADIUM_HOST;
import static demo.mesharena.common.Commons.STADIUM_PORT;

public class AI extends AbstractVerticle {

  private static final Optional<Tracer> TRACER = Commons.createTracerFromEnv();
  private static final long DELTA_MS = 300;
  private static final double IDLE_TIMER = 2.0;
  private static final double ROLE_TIMER = 10.0;
  private static final String NAMES = Commons.getStringEnv("PLAYER_NAMES", "Goat,Sheep,Cow,Chicken,Pig,Lamb");
  private static final String COLOR = Commons.getStringEnv("PLAYER_COLOR", "blue");
  private static final String TEAM = Commons.getStringEnv("PLAYER_TEAM", "locals");
  // Speed = open scale
  private static final double SPEED = Commons.getIntEnv("PLAYER_SPEED", 60);
  // Accuracy [0, 1]
  private static final double ACCURACY = Commons.getDoubleEnv("PLAYER_ACCURACY", 0.8);
  private static final double MIN_SPEED = ACCURACY * SPEED;
  // Skill = open scale
  private static final int SKILL = Commons.getIntEnv("PLAYER_SKILL", 5);
  // Shoot strength = open scale
  private static final double SHOOT_STRENGTH = Commons.getIntEnv("PLAYER_SHOOT", 250);
  // Attacking / defending? (more is attacking) [0, 100]
  private static final int ATTACKING = Commons.getIntEnv("PLAYER_ATTACKING", 65);
  // While attacking, will shoot quickly? [0, 100]
  private static final int ATT_SHOOT_FAST = Commons.getIntEnv("PLAYER_ATT_SHOOT_FAST", 20);
  // While defending, will shoot quickly? [0, 100]
  private static final int DEF_SHOOT_FAST = Commons.getIntEnv("PLAYER_DEF_SHOOT_FAST", 40);
  private static final boolean INTERACTIVE_MODE = Commons.getIntEnv("INTERACTIVE_MODE", 0) == 1;

  private final Random rnd = new SecureRandom();
  private final WebClient client;
  private final String id;
  private final boolean isVisitors;
  private final JsonObject json;
  private final DisplayMessager displayMessager;
  private Point pos = Point.ZERO;
  private ArenaInfo arenaInfo;
  private Point currentDestination = null;
  private Point defendPoint = null;
  private double idleTimer = -1;
  private double roleTimer = -1;
  private final String name;

  private enum Role { ATTACK, DEFEND }
  private Role role;

  public AI(Vertx vertx) {
    client = WebClient.create(vertx);
    id = UUID.randomUUID().toString();
    String[] names = NAMES.split(",");
    int i = rnd.nextInt(names.length);
    name = names[i];
    this.isVisitors = !TEAM.equals("locals");
    json = new JsonObject()
        .put("id", id)
        .put("style", "position: absolute; background-color: " + COLOR + "; transition: top " + DELTA_MS + "ms, left " + DELTA_MS + "ms; height: 30px; width: 30px; border-radius: 50%; z-index: 8;")
        .put("name", name)
        .put("ip", Commons.getIP());
    displayMessager = new DisplayMessager(vertx, client, TRACER);
  }

  public static void main(String[] args) {
    Vertx vertx = Commons.vertx(TRACER);
    vertx.deployVerticle(new AI(vertx));
  }

  @Override
  public void start() {
    // Start metrics server
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(8080);

    Router router = Router.router(vertx);
    router.get("/health").handler(ctx -> ctx.response().end());
    router.get("/tryShoot").handler(this::tryShoot);

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create());
    }
    vertx.createHttpServer().requestHandler(router)
        .listen(serverOptions.getPort(), serverOptions.getHost());

    // First display
    display();

    // Check regularly about arena info
    checkArenaInfo();
    vertx.setPeriodic(5000, loopId -> checkArenaInfo());

    // Start game loop
    vertx.setPeriodic(DELTA_MS, loopId -> update((double)DELTA_MS / 1000.0));
  }

  private void checkArenaInfo() {
    client.get(STADIUM_PORT, STADIUM_HOST, "/info").sendJson(
        new JsonObject().put("isVisitors", isVisitors),
        ar -> {
          if (!ar.succeeded()) {
            ar.cause().printStackTrace();
            arenaInfo = null;
          } else {
            HttpResponse<Buffer> response = ar.result();
            JsonObject obj = response.bodyAsJsonObject();
            double goalX = obj.getDouble("goalX");
            double goalY = obj.getDouble("goalY");
            int defendZoneTop = obj.getInteger("defendZoneTop");
            int defendZoneBottom = obj.getInteger("defendZoneBottom");
            int defendZoneLeft = obj.getInteger("defendZoneLeft");
            int defendZoneRight = obj.getInteger("defendZoneRight");

            arenaInfo = new ArenaInfo(defendZoneTop, defendZoneLeft, defendZoneBottom, defendZoneRight, new Point(goalX, goalY));
          }
        });
  }

  private void update(double delta) {
    if (idleTimer > 0) {
      idleTimer -= delta;
      walkRandom(delta);
    } else {
      roleTimer -= delta;
      if (roleTimer < 0) {
        roleTimer = ROLE_TIMER;
        chooseRole();
      }
      lookForBall(delta, false, null);
    }
  }

  private void chooseRole() {
    if (rnd.nextInt(100) > ATTACKING) {
      role = Role.DEFEND;
      if (arenaInfo == null) {
        defendPoint = randomDestination();
      } else {
        Point dimension = arenaInfo.defendZoneTLBR.derivate();
        defendPoint = new Point(rnd.nextInt((int) dimension.x()), rnd.nextInt((int) dimension.y()))
          .add(arenaInfo.defendZoneTLBR.start());
      }
    } else {
      role = Role.ATTACK;
    }
  }

  private void tryShoot(RoutingContext ctx) {
    lookForBall(0, true, OpenTracingUtil.getSpan());
    ctx.response().end();
  }

  private void lookForBall(double delta, boolean isHumanShot, Span currentSpan) {
    JsonObject json = new JsonObject()
        .put("playerX", pos.x())
        .put("playerY", pos.y())
        .put("playerSkill", SKILL)
        .put("playerID", id)
        .put("playerName", name)
        .put("playerTeam", TEAM);

    HttpRequest<Buffer> request = client.get(BALL_PORT, BALL_HOST, "/hasControl");
    Optional<Span> ballControlSpan = TRACER.map(tracer -> {
      Span span = tracer.buildSpan("Ball control")
          .withTag("name", name)
          .withTag("id", id)
          .asChildOf(currentSpan)
          .start();
      OpenTracingUtil.setSpan(span);
      return span;
    });

    request.sendJsonObject(json)
        .map(resp -> {
          if (resp.statusCode() == 200) {
            return resp.bodyAsJsonObject();
          }
          return null;
        })
        .onComplete(ar -> {
          ballControlSpan.ifPresent(Span::finish);
          if (!ar.succeeded() || ar.result() == null) {
            if (!isHumanShot) {
              // No ball? What a pity. Walk randomly in sadness.
              idle();
            }
          } else {
            JsonObject obj = ar.result();
            if (!isHumanShot) {
              double x = obj.getDouble("x");
              double y = obj.getDouble("y");
              Point ball = new Point(x, y);
              if (role == Role.ATTACK || pos.diff(ball).size() < 70) {
                // Go to the ball
                currentDestination = ball;
              } else {
                currentDestination = defendPoint;
              }
            }
            if (Boolean.TRUE.equals(obj.getBoolean("success"))
                && (isHumanShot || !INTERACTIVE_MODE)) {
              // Run on context, so that active span that is set in "shoot" doesn't gets erased too early upon "hasControl" response processed
              vertx.runOnContext(v ->
                  shoot(Boolean.TRUE.equals(obj.getBoolean("takesBall")), ballControlSpan.map(Span::context))
              );
            }
            if (!isHumanShot) {
              walkToDestination(delta);
            }
          }
        });
  }

  private Point shootVec(Point direction, double baseStrength) {
    // From 50% to 100% of base strength
    double strength = baseStrength * (0.5 + rnd.nextDouble() * 0.5);
    return direction.mult(strength);
  }

  private void shoot(boolean takesBall, Optional<SpanContext> spanContext) {
    final Point shootVector;
    final String kind;
    Point direction = randomishSegmentNormalized(
        new Segment(pos, (arenaInfo == null) ? randomDestination() : arenaInfo.goal));
    if (role == Role.ATTACK) {
      // Go forward or try to shoot
      int rndNum = rnd.nextInt(100);
      if (rndNum < ATT_SHOOT_FAST) {
        // Try to shoot (if close enough to ball)
        shootVector = shootVec(direction, SHOOT_STRENGTH);
        kind = "togoal";
      } else {
        // Go forward
        shootVector = shootVec(direction, SPEED * 1.8);
        kind = takesBall ? "control" : "forward";
      }
    } else {
      // Defensive shoot
      // Go forward or defensive shoot
      int rndNum = rnd.nextInt(100);
      if (rndNum < DEF_SHOOT_FAST) {
        // Defensive shoot, randomise a second time, shoot stronger
        direction = randomishSegmentNormalized(new Segment(pos, pos.add(direction)));
        shootVector = shootVec(direction, SHOOT_STRENGTH * 1.5);
        kind = "defensive";
      } else {
        // Go forward
        shootVector = shootVec(direction, SPEED * 1.8);
        kind = takesBall ? "control" : "forward";
      }
    }
    JsonObject json = new JsonObject()
        .put("dx", shootVector.x())
        .put("dy", shootVector.y())
        .put("kind", kind)
        .put("playerID", id);

    HttpRequest<Buffer> request = client.put(BALL_PORT, BALL_HOST, "/shoot");
    Optional<Span> shootSpan = spanContext.map(sc -> {
      Span span = TRACER.get().buildSpan("Player shoot")
        .withTag("name", name)
        .withTag("id", id)
        .withTag("kind", kind)
        .withTag("strength", shootVector.size())
        .asChildOf(sc)
        .start();
      OpenTracingUtil.setSpan(span);
      return span;
    });
    request.sendJson(json, ar -> shootSpan.ifPresent(Span::finish));
  }

  private void idle() {
    idleTimer = IDLE_TIMER;
  }

  private Point randomDestination() {
    return new Point(rnd.nextInt(500), rnd.nextInt(500));
  }

  private void walkRandom(double delta) {
    if (currentDestination == null || new Segment(pos, currentDestination).size() < 10) {
      currentDestination = randomDestination();
    }
    walkToDestination(delta);
  }

  private void walkToDestination(double delta) {
    if (currentDestination != null) {
      // Speed and angle are modified by accuracy
      Segment segToDest = new Segment(pos, currentDestination);
      // maxSpeed avoids stepping to high when close to destination
      double maxSpeed = Math.min(segToDest.size() / delta, SPEED);
      // minSpeed must be kept <= maxSpeed
      double minSpeed = Math.min(maxSpeed, MIN_SPEED);
      double speed = delta * (minSpeed + rnd.nextDouble() * (maxSpeed - minSpeed));
      Point relativeMove = randomishSegmentNormalized(segToDest).mult(speed);
      pos = pos.add(relativeMove);
      display();
    }
  }

  private Point randomishSegmentNormalized(Segment segToDest) {
    double angle = rnd.nextDouble() * (1.0 - ACCURACY) * Math.PI;
    if (rnd.nextInt(2) == 0) {
      angle *= -1;
    }
    return segToDest.derivate().normalize().rotate(angle);
  }

  private void display() {
    json.put("x", pos.x() - 15)
        .put("y", pos.y() - 15);
    displayMessager.send(json);
  }

  private static class ArenaInfo {
    private final Segment defendZoneTLBR;
    private final Point goal;

    private ArenaInfo(int defendZoneTop, int defendZoneLeft, int defendZoneBottom, int defendZoneRight, Point goal) {
      this.defendZoneTLBR = new Segment(new Point(defendZoneLeft, defendZoneTop), new Point(defendZoneRight, defendZoneBottom));
      this.goal = goal;
    }
  }
}
