package demo.mesharena.ball;

import demo.mesharena.common.Commons;
import demo.mesharena.common.Point;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import static demo.mesharena.common.Commons.STADIUM_HOST;
import static demo.mesharena.common.Commons.STADIUM_PORT;
import static demo.mesharena.common.Commons.UI_HOST;
import static demo.mesharena.common.Commons.UI_PORT;

public class Ball extends AbstractVerticle {

  private static final long DELTA_MS = 100;
  private static final double RESISTANCE = 80;
  private static final double PCT_ERRORS = Commons.getIntEnv("PCT_ERRORS", 0);

  private final WebClient client;
  private final String id;
  private final Random rnd = new SecureRandom();
  private final JsonObject json;
  private Point speed = Point.ZERO;
  private String controllingPlayer;
  private String controllingPlayerName;
  private int controllingPlayerSkill;
  private double controllingPlayerSkillTimer;
  private Point pos = Point.ZERO;

  private Ball(Vertx vertx) {
    client = WebClient.create(vertx);
    id = "ball-" + UUID.randomUUID().toString();
    json = new JsonObject()
        .put("id", id)
        .put("style", "position: absolute; background-image: url(./ball.png); width: 20px; height: 20px; z-index: 5; transition: top " + DELTA_MS + "ms, left " + DELTA_MS + "ms;")
        .put("text", "");
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new Ball(vertx));
  }

  @Override
  public void start() throws Exception {
    // Register ball API
    Router router = Router.router(vertx);
    router.get("/health").handler(ctx -> ctx.response().end());

    router.put("/shoot").handler(this::shoot);
    router.get("/interact").handler(this::interact);
    router.put("/setPosition").handler(this::setPosition);
    vertx.createHttpServer().requestHandler(router::accept).listen(Commons.BALL_PORT, Commons.BALL_HOST);

    // Ping-display
    vertx.setPeriodic(2000, loopId -> this.display());

    // Start game loop
    vertx.setPeriodic(DELTA_MS, loopId -> this.update((double)DELTA_MS / 1000.0));
  }

  private void interact(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      if (rnd.nextInt(100) < PCT_ERRORS) {
        ctx.response().setStatusCode(503).setStatusMessage("faiiiiilure! (to test outlier detection)").end();
        return;
      }
      JsonObject input = buf.toJsonObject();
      double playerX = input.getDouble("playerX");
      double playerY = input.getDouble("playerY");
      double shootX = input.getDouble("shootX");
      double shootY = input.getDouble("shootY");
      String playerID = input.getString("playerID");
      int playerSkill = input.getInteger("playerSkill");
      String playerName = input.getString("playerName");
      JsonObject output = new JsonObject()
          .put("x", pos.x())
          .put("y", pos.y());

      double distanceToBall = pos.diff(new Point(playerX, playerY)).size();
      if (distanceToBall < 15) {
        if (playerID.equals(controllingPlayer)) {
          if (distanceToBall < 5) {
            // Shoot
            controllingPlayerSkill = playerSkill;
            speed = new Point(shootX, shootY).diff(pos);
            int commentRnd = rnd.nextInt(2);
            if (speed.size() > 200) {
              if (commentRnd == 0) {
                comment("Tir de " + playerName + "!");
              } else {
                comment("Attention " + playerName + " tente sa chance!");
              }
            } else {
              if (commentRnd == 0) {
                comment("Toujours " + playerName + "...");
              } else {
                comment("Encore " + playerName + "...");
              }
            }
          }
        } else if (controllingPlayer == null || rnd.nextInt(2 * controllingPlayerSkill + playerSkill) < playerSkill) {
          controllingPlayer = playerID;
          controllingPlayerSkill = playerSkill;
          controllingPlayerName = playerName;
          // Shoot
          speed = new Point(shootX, shootY).diff(pos);
          double size = speed.size();
          if (size > 310) {
            comment("Dégagement de " + playerName);
          } else if (size > 200) {
            comment(playerName + " s'empare du ballon et tire!");
          } else {
            comment(playerName + " récupère le ballon");
          }
        }
      }
      ctx.response().end(output.toString());
    });
  }

  private void comment(String text) {
    JsonObject json = new JsonObject()
        .put("id", "ball-comment")
        .put("style", "position: absolute; color: brown; font-weight: bold; z-index: 10; top: " + (pos.y() + 10) + "px; left: " + (pos.x() - 10) + "px;")
        .put("text", text);

    client.post(UI_PORT, UI_HOST, "/display").sendJson(json, ar -> {
      if (!ar.succeeded()) {
        ar.cause().printStackTrace();
      }
    });
  }

  private void setPosition(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      double x = json.getDouble("x");
      double y = json.getDouble("y");
      pos = new Point(x, y);
      speed = Point.ZERO;
      controllingPlayer = null;
      controllingPlayerSkill = 0;
      ctx.response().end();
      display();
    });
  }

  private void shoot(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      double dx = json.getDouble("dx");
      double dy = json.getDouble("dy");
      speed = new Point(dx, dy);
      controllingPlayer = null;
      controllingPlayerSkill = 0;
      ctx.response().end();
    });
  }

  private void update(double delta) {
    double oldSpeed = speed.size();
    if (oldSpeed > 0) {
      Point oldPos = pos;
      Point newPos = pos.add(speed.mult(delta));
      double newSpeed = Math.max(0, oldSpeed - RESISTANCE * delta);
      checkBounce(pos, newPos, newSpeed, didBounce -> {
        if (!didBounce) {
          pos = newPos;
          speed = speed.mult(newSpeed / oldSpeed);
        }
        if (!oldPos.equals(pos)) {
          display();
        }
      });
    }
    // Decrease controlling skill
    if (controllingPlayerSkill > 0) {
      controllingPlayerSkillTimer += delta;
      if (controllingPlayerSkillTimer >= 0.5) {
        controllingPlayerSkill--;
        controllingPlayerSkillTimer = 0;
      }
    }
  }

  private void checkBounce(Point oldPos, Point newPos, double newSpeed, Handler<Boolean> handler) {
    JsonObject json = new JsonObject()
        .put("xStart", oldPos.x())
        .put("yStart", oldPos.y())
        .put("xEnd", newPos.x())
        .put("yEnd", newPos.y());

    client.post(STADIUM_PORT, STADIUM_HOST, "/bounce").sendJson(json, ar -> {
      if (!ar.succeeded()) {
        // No stadium => no bounce
        handler.handle(false);
      } else {
        HttpResponse<Buffer> response = ar.result();
        JsonObject obj = response.bodyAsJsonObject();
        if (obj.containsKey("scored")) {
          comment("Et BUUUUT de " + controllingPlayerName + " !!!!");
          // Do not update position, Stadium will do it
          controllingPlayer = null;
          speed = Point.ZERO;
          handler.handle(true);
        } else if (obj.containsKey("x")) {
          // Contains bounce data
          double x = obj.getDouble("x");
          double y = obj.getDouble("y");
          pos = new Point(x, y);
          double normDx = obj.getDouble("dx");
          double normDy = obj.getDouble("dy");
          speed = new Point(normDx * newSpeed, normDy * newSpeed);
          handler.handle(true);
        } else {
          handler.handle(false);
        }
      }
    });
  }

  private void display() {
    json.put("x", pos.x() - 10)
        .put("y", pos.y() - 10);

    client.post(UI_PORT, UI_HOST, "/display").sendJson(json, ar -> {
      if (!ar.succeeded()) {
        ar.cause().printStackTrace();
      }
    });
  }
}
