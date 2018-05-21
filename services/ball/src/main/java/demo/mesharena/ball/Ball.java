package demo.mesharena.ball;

import demo.mesharena.stadium.Commons;
import demo.mesharena.stadium.Point;
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

public class Ball extends AbstractVerticle {

  private static final long DELTA_MS = 100;
  private static final double RESISTANCE = 80;

  private Point pos = Point.ZERO;
  private Point speed = Point.ZERO;
  private boolean isSticked;

  private Ball() {
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new Ball());
  }

  @Override
  public void start() throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(Commons.UI_HOST).setDefaultPort(Commons.UI_PORT));
    HttpClientRequest request = client.request(HttpMethod.POST, "/creategameobject", response -> {});

    // Register game object
    String json = new JsonObject()
        .put("id", "ball")
        .put("style", "position: absolute; background-color: red;")
        .put("text", "ball")
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();

    display();

    // Register ball API
    Router router = Router.router(vertx);
    router.put("/shoot").handler(this::shoot);
    router.put("/setPosition").handler(this::setPosition);
    vertx.createHttpServer().requestHandler(router::accept).listen(Commons.BALL_PORT, Commons.BALL_HOST);

    // Start game loop
    vertx.setPeriodic(DELTA_MS, loopId -> this.update((double)DELTA_MS / 1000.0));
  }

  private void setPosition(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      double x = json.getDouble("x");
      double y = json.getDouble("y");
      pos = new Point(x, y);
      speed = Point.ZERO;
      isSticked = false;
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
      isSticked = false;
      ctx.response().end();
    });
  }

  private void update(double delta) {
    if (isSticked) {
      // TODO: special movement when a player owns the ball
    } else {
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
  }

  private void checkBounce(Point oldPos, Point newPos, double newSpeed, Handler<Boolean> handler) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(Commons.STADIUM_HOST).setDefaultPort(Commons.STADIUM_PORT));
    HttpClientRequest request = client.request(HttpMethod.POST, "/bounce", response -> {
      response.bodyHandler(buf -> {
        JsonObject obj = buf.toJsonObject();
        if (obj.containsKey("scored")) {
          // Do not update position, Stadium will do it
          isSticked = false;
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
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
    HttpClientRequest request = client.request(HttpMethod.POST, "/movegameobject", response -> {});

    String json = new JsonObject()
        .put("id", "ball")
        .put("x", pos.x())
        .put("y", pos.y())
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
  }
}
