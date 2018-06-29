package demo.mesharena.ball;

import demo.mesharena.common.Commons;
import demo.mesharena.common.Point;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.security.SecureRandom;
import java.util.Random;

import static demo.mesharena.common.Commons.UI_HOST;
import static demo.mesharena.common.Commons.UI_PORT;

public class Ball extends AbstractVerticle {

  private static final long DELTA_MS = 100;
  private static final double RESISTANCE = 80;

  private final Random rnd = new SecureRandom();
  private final JsonObject json;
  private Point speed = Point.ZERO;
  private String controllingPlayer;
  private int controllingPlayerSkill;
  private Point pos = Point.ZERO;

  private Ball() {
    json = new JsonObject()
        .put("id", "ball")
        .put("style", "position: absolute; background-color: red;")
        .put("text", "ball");
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new Ball());
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

    // First display
    display();

    // Start game loop
    vertx.setPeriodic(DELTA_MS, loopId -> this.update((double)DELTA_MS / 1000.0));
  }

  private void interact(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject input = buf.toJsonObject();
      double playerX = input.getDouble("playerX");
      double playerY = input.getDouble("playerY");
      double shootX = input.getDouble("shootX");
      double shootY = input.getDouble("shootY");
      String playerID = input.getString("playerID");
      int playerSkill = input.getInteger("playerSkill");
      JsonObject output = new JsonObject()
          .put("x", pos.x())
          .put("y", pos.y());

      double distanceToBall = pos.diff(new Point(playerX, playerY)).size();
      if (distanceToBall < 15) {
        if (playerID.equals(controllingPlayer)) {
          if (distanceToBall < 5) {
            // Shoot
            speed = new Point(shootX, shootY).diff(pos);
          }
        } else if (controllingPlayer == null || rnd.nextInt(2 * controllingPlayerSkill + playerSkill) < playerSkill) {
          controllingPlayer = playerID;
          controllingPlayerSkill = playerSkill;
          // Shoot
          speed = new Point(shootX, shootY).diff(pos);
        }
      }
      ctx.response().end(output.toString());
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
  }

  private void checkBounce(Point oldPos, Point newPos, double newSpeed, Handler<Boolean> handler) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(Commons.STADIUM_HOST).setDefaultPort(Commons.STADIUM_PORT));
    HttpClientRequest request = client.request(HttpMethod.POST, "/bounce", response -> {
      response.bodyHandler(buf -> {
        JsonObject obj = buf.toJsonObject();
        if (obj.containsKey("scored")) {
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
      });
    }).exceptionHandler(t -> {
      // No stadium => no bounce
      handler.handle(false);
    });

    String json = new JsonObject()
        .put("xStart", oldPos.x())
        .put("yStart", oldPos.y())
        .put("xEnd", newPos.x())
        .put("yEnd", newPos.y())
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
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
}
