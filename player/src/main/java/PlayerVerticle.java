import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.PrometheusScrapingHandler;

import java.security.SecureRandom;
import java.util.UUID;

public class PlayerVerticle extends AbstractVerticle {
  private final int deltaMs = Commons.getIntEnv("DELTA_MS", 300);
  private final String team = Commons.getStringEnv("PLAYER_TEAM", "locals");
  private final String color = Commons.getStringEnv("PLAYER_COLOR", "blue");
  // Speed = open scale
  private final double speed = Commons.getDoubleEnv("PLAYER_SPEED", 60);
  // Accuracy [0, 1]
  private final double accuracy = Commons.getDoubleEnv("PLAYER_ACCURACY", 0.8);
  private final double minSpeed = accuracy * speed;
  // Skill = open scale
  private final int skill = Commons.getIntEnv("PLAYER_SKILL", 5);
  // Shoot strength = open scale
  private final double shootStrength = Commons.getDoubleEnv("PLAYER_SHOOT", 250);
  // While attacking, will shoot quickly? [0, 100]
  private final int attShootFast = Commons.getIntEnv("PLAYER_ATT_SHOOT_FAST", 20);
  private final boolean interactiveMode = Commons.getBoolEnv("INTERACTIVE_MODE", false);

  private final SecureRandom rnd = new SecureRandom();
  private final String id = UUID.randomUUID().toString();
  private final GameObject aiGO;
  private final boolean isVisitors = !team.equals("locals");
  private final WebClient client;
  private final Displayer displayer;
  private Point pos = new Point(0.0, 0.0);
  private ArenaInfo arenaInfo = null;
  private Point currentDestination = null;
  private double idleTimer = -1.0;

  public PlayerVerticle(WebClient client, Displayer displayer, String name) {
    this.client = client;
    this.displayer = displayer;
    aiGO = new GameObject(
      id,
      new Style()
        .bgColor(color)
        .transition(deltaMs)
        .dimensions(30, 30)
        .zIndex(8)
        .other("border-radius: 50%;")
        .toString(),
      0, 0, "", new PlayerRef(name, Commons.getIP(), Commons.PLAYER_PORT));
  }

  @Override
  public void start() throws Exception {
    var router = Router.router(vertx);

    if (Commons.METRICS_ENABLED) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create());
    }

    router.route("/health").handler(it -> it.response().end());
    router.route("/tryShoot").handler(this::tryShoot);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(Commons.PLAYER_PORT)
      .onSuccess(server ->
        System.out.println("HTTP server started on port " + server.actualPort())
      );

    // First display
    display();

    // Check regularly about arena info
    checkArenaInfo();
    vertx.setPeriodic(5000, a -> checkArenaInfo());

    // Start game loop
    vertx.setPeriodic(deltaMs, a -> update(deltaMs / 1000.0));
  }

  private void checkArenaInfo() {
    client.get(Commons.STADIUM_PORT, Commons.STADIUM_HOST, "/info")
      .timeout(1000)
      .sendJson(new JsonObject().put("isVisitors", isVisitors), it -> {
        if (!it.succeeded()) {
          it.cause().printStackTrace();
          arenaInfo = null;
        } else {
          arenaInfo = it.result().bodyAsJsonObject().mapTo(ArenaInfo.class);
        }
      });
  }

  private void update(double delta) {
    if (idleTimer > 0) {
      idleTimer -= delta;
      walkRandom(delta);
    } else {
      lookForBall(delta, false);
    }
  }

  private Point randomDestination() {
    return new Point(rnd.nextInt(500), rnd.nextInt(500));
  }

  private void walkRandom(double delta) {
    if (currentDestination == null || pos.dist(currentDestination) < 10) {
      currentDestination = randomDestination();
    }
    walkToDestination(delta);
  }

  private void walkToDestination(double delta) {
    if (currentDestination != null) {
      // Speed and angle are modified by accuracy
      var segToDest = new Segment(pos, currentDestination);
      // maxSpeed avoids stepping to high when close to destination
      var maxSpeed = Math.min(segToDest.size() / delta, speed);
      // minSpeed must be kept <= maxSpeed
      var minSpeed = Math.min(maxSpeed, this.minSpeed);
      var speed = delta * (minSpeed + rnd.nextDouble() * (maxSpeed - minSpeed));
      Point relativeMove = randomishSegmentNormalized(segToDest).mult(speed);
      pos = pos.add(relativeMove);
      display();
    }
  }

  private Point randomishSegmentNormalized(Segment segToDest) {
    var angle = rnd.nextDouble() * (1.0 - accuracy) * Math.PI;
    if (rnd.nextInt(2) == 0) {
      angle *= -1.0;
    }
    return segToDest.derivate().normalize().rotate(angle);
  }

  private void display() {
    displayer.send(aiGO.withX(pos.x() - 15).withY(pos.y() - 15));
  }

  private void tryShoot(RoutingContext ctx) {
    lookForBall(0.0, true);
    ctx.response().end();
  }

  private void lookForBall(double delta, boolean isHumanShot) {
    var rq = new BallControl.Rq(pos, new BallControl.Player(id, skill, aiGO.playerRef().name(), team));
    client.get(Commons.BALL_PORT, Commons.BALL_HOST, "/hasControl")
      .timeout(1000)
      .sendJson(rq)
      .onFailure(Throwable::printStackTrace)
      .map(it -> {
        if (it.statusCode() == 200) {
          return it.bodyAsJsonObject();
        }
        return null;
      })
      .onComplete(it -> {
        var res = it.result();
        var rs = res != null ? it.result().mapTo(BallControl.Rs.class) : null;
        if (res == null || rs == null || !it.succeeded()) {
          if (!isHumanShot) {
            // No ball? What a pity. Walk randomly in sadness.
            idle();
          }
        } else {
          if (!isHumanShot) {
            currentDestination = rs.pos();
          }
          if (rs.success() && (isHumanShot || !interactiveMode)) {
            shoot(rs.takesBall());
          }
          if (!isHumanShot) {
            walkToDestination(delta);
          }
        }
      });
  }

  private void idle() {
    idleTimer = 2.0;
  }

  private Point shootVec(Point direction, double baseStrength) {
    // From 50% to 100% of base strength
    var strength = baseStrength * (0.5 + rnd.nextDouble() * 0.5);
    return direction.mult(strength);
  }

  private void shoot(boolean takesBall) {
    final Point shootVector;
    final String kind;
    var direction = randomishSegmentNormalized(
      new Segment(pos, arenaInfo != null ? arenaInfo.goal() : randomDestination()));
    // Go forward or try to shoot
    var rndNum = rnd.nextInt(100);
    if (rndNum < attShootFast) {
      // Try to shoot (if close enough to ball)
      shootVector = shootVec(direction, shootStrength);
      kind = "togoal";
    } else {
      // Go forward
      shootVector = shootVec(direction, speed * 1.8);
      kind = takesBall ? "control" : "forward";
    }
    var rq = new BallShoot.Rq(shootVector, kind, id);
    client.put(Commons.BALL_PORT, Commons.BALL_HOST, "/shoot")
      .timeout(1000)
      .sendJson(JsonObject.mapFrom(rq));
  }

  public static void main(String[] args) {
    var vertx = Commons.vertx();
    var client = WebClient.create(vertx);
    var displayer = new Displayer(vertx, client);
    Names.getName(client).onFailure(Throwable::printStackTrace)
      .onSuccess(name -> vertx.deployVerticle(new PlayerVerticle(client, displayer, name)));
  }
}
