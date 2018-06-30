package demo.mesharena.ai;

import demo.mesharena.common.Commons;
import demo.mesharena.common.Point;
import demo.mesharena.common.Segment;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import static demo.mesharena.common.Commons.UI_HOST;
import static demo.mesharena.common.Commons.UI_PORT;

public abstract class AI extends AbstractVerticle {

  private static final long DELTA_MS = 300;
  private static final double IDLE_TIMER = 2.0;
  private static final double ROLE_TIMER = 10.0;
  private static final String NAME = Commons.getStringEnv("PLAYER_NAME", "Goat");
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

  private final Random rnd = new SecureRandom();
  private final String id;
  private final boolean isVisitors;
  private final JsonObject json;
  private Point pos = Point.ZERO;
  private ArenaInfo arenaInfo;
  private Point currentDestination = null;
  private Point defendPoint = null;
  private double idleTimer = -1;
  private double roleTimer = -1;

  private enum Role { ATTACK, DEFEND }
  private Role role;

  public AI(boolean isVisitors) {
    id = UUID.randomUUID().toString();
    this.isVisitors = isVisitors;
    json = new JsonObject()
        .put("id", id)
        .put("style", "position: absolute; background-color: " + (isVisitors ? "pink" : "purple") + "; transition: top " + DELTA_MS + "ms, left " + DELTA_MS + "ms;")
        .put("text", NAME);
  }

  @Override
  public void start() throws Exception {
    // First display
    display();

    // Start game loop
    vertx.setPeriodic(DELTA_MS, loopId -> update((double)DELTA_MS / 1000.0));

    // Check regularly about arena info
    checkArenaInfo();
    vertx.setPeriodic(5000, loopId -> checkArenaInfo());
  }

  private void checkArenaInfo() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(Commons.STADIUM_HOST).setDefaultPort(Commons.STADIUM_PORT));
    HttpClientRequest request = client.request(HttpMethod.GET, "/info", response -> {
      response.bodyHandler(buf -> {
        JsonObject obj = buf.toJsonObject();
        double goalX = obj.getDouble("goalX");
        double goalY = obj.getDouble("goalY");
        int defendZoneTop = obj.getInteger("defendZoneTop");
        int defendZoneBottom = obj.getInteger("defendZoneBottom");
        int defendZoneLeft = obj.getInteger("defendZoneLeft");
        int defendZoneRight = obj.getInteger("defendZoneRight");
        arenaInfo = new ArenaInfo(defendZoneTop, defendZoneLeft, defendZoneBottom, defendZoneRight, new Point(goalX, goalY));
      });
    }).exceptionHandler(t -> {
      System.out.println("Exception: " + t);
      arenaInfo = null;
    });

    String json = new JsonObject()
        .put("isVisitors", isVisitors)
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
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
      lookForBall(delta);
    }
  }

  private void chooseRole() {
    if (rnd.nextInt(100) > ATTACKING) {
      role = Role.DEFEND;
      if (arenaInfo == null) {
        defendPoint = randomDestination();
      } else {
        int dextX = arenaInfo.defendZoneLeft + rnd.nextInt(arenaInfo.defendZoneRight - arenaInfo.defendZoneLeft);
        int dextY = arenaInfo.defendZoneTop + rnd.nextInt(arenaInfo.defendZoneBottom - arenaInfo.defendZoneTop);
        defendPoint = new Point(dextX, dextY);
      }
    } else {
      role = Role.ATTACK;
    }
  }

  private void lookForBall(double delta) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(Commons.BALL_HOST).setDefaultPort(Commons.BALL_PORT));
    HttpClientRequest request = client.request(HttpMethod.GET, "/interact", response -> {
      response.bodyHandler(buf -> {
        JsonObject obj = buf.toJsonObject();
        double x = obj.getDouble("x");
        double y = obj.getDouble("y");
        if (role == Role.ATTACK) {
          // Go to the ball
          currentDestination = new Point(x, y);
        } else {
          defend(new Point(x, y));
        }
        walkToDestination(delta);
      });
    }).exceptionHandler(t -> {
      // No ball? What a pity. Walk randomly in sadness.
      idle();
    });

    final Point shootDest;
    Point goal = (arenaInfo == null) ? randomDestination() : arenaInfo.goal;
    if (role == Role.ATTACK) {
      Point direction = randomishSegmentNormalized(new Segment(pos, goal));
      // Go forward or try to shoot
      int rndNum = rnd.nextInt(100);
      if (rndNum < ATT_SHOOT_FAST) {
        // Try to shoot (if close enough to ball)
        shootDest = direction.mult(SHOOT_STRENGTH).add(pos);
      } else {
        // Go forward
        shootDest = direction.mult(SPEED * 1.8).add(pos);
      }
    } else {
      // Defensive shoot
      Point direction = randomishSegmentNormalized(new Segment(pos, goal));
      // Go forward or defensive shoot
      int rndNum = rnd.nextInt(100);
      if (rndNum < DEF_SHOOT_FAST) {
        // Defensive shoot, randomise a second time, shoot stronger
        direction = randomishSegmentNormalized(new Segment(pos, pos.add(direction)));
        shootDest = direction.mult(SHOOT_STRENGTH * 1.5).add(pos);
      } else {
        // Go forward
        shootDest = direction.mult(SPEED * 1.8).add(pos);
      }
    }
    String json = new JsonObject()
        .put("playerX", pos.x())
        .put("playerY", pos.y())
        .put("shootX", shootDest.x())
        .put("shootY", shootDest.y())
        .put("playerSkill", SKILL)
        .put("playerID", id)
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
  }

  private void defend(Point ball) {
    // Is the ball in my side of the field?
    if (arenaInfo != null && ball.x() >= arenaInfo.defendZoneLeft && ball.x() <= arenaInfo.defendZoneRight
        && ball.y() >= arenaInfo.defendZoneTop && ball.y() <= arenaInfo.defendZoneBottom) {
      // Go to the ball
      currentDestination = ball;
    } else {
      currentDestination = defendPoint;
    }
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
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(UI_HOST).setDefaultPort(UI_PORT));
    HttpClientRequest request = client.request(HttpMethod.POST, "/display", response -> {});

    json.put("x", pos.x())
        .put("y", pos.y());
    String strJson = json.toString();

    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(strJson.length()));
    request.write(strJson);
    request.end();
  }

  private static class ArenaInfo {
    private final int defendZoneTop;
    private final int defendZoneLeft;
    private final int defendZoneBottom;
    private final int defendZoneRight;
    private final Point goal;

    private ArenaInfo(int defendZoneTop, int defendZoneLeft, int defendZoneBottom, int defendZoneRight, Point goal) {
      this.defendZoneTop = defendZoneTop;
      this.defendZoneLeft = defendZoneLeft;
      this.defendZoneBottom = defendZoneBottom;
      this.defendZoneRight = defendZoneRight;
      this.goal = goal;
    }
  }
}
