package demo.mesharena.ball;

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

  private double x;
  private double y;
  private double dx;
  private double dy;
  private boolean isSticked;

  private Ball() {
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new Ball());
  }

  @Override
  public void start() throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
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
    vertx.createHttpServer().requestHandler(router::accept).listen(8081);

    // Start game loop
    vertx.setPeriodic(DELTA_MS, loopId -> this.update((double)DELTA_MS / 1000.0));
  }

  private void setPosition(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      x = json.getDouble("x");
      y = json.getDouble("y");
      dx = dy = 0;
      isSticked = false;
      ctx.response().end();
      display();
    });
  }

  private void shoot(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      System.out.println(json);
      dx = json.getDouble("dx");
      dy = json.getDouble("dy");
      isSticked = false;
      ctx.response().end();
    });
  }

  private void update(double delta) {
    if (isSticked) {
      // TODO: special movement when a player owns the ball
    } else {
      double oldSpeed = vecSize(dx, dy);
      if (oldSpeed > 0) {
        double oldX = x;
        double oldY = y;
        double newX = x + delta * dx;
        double newY = y + delta * dy;
        double newSpeed = Math.max(0, oldSpeed - RESISTANCE * delta);
        checkBounce(x, y, newX, newY, newSpeed, didBounce -> {
          if (!didBounce) {
            x = newX;
            y = newY;
            dx = dx * newSpeed / oldSpeed;
            dy = dy * newSpeed / oldSpeed;
          }
          if (oldX != x || oldY != y) {
            display();
          }
        });
      }
    }
  }

  private void checkBounce(double oldX, double oldY, double newX, double newY, double newSpeed, Handler<Boolean> handler) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8082));
    HttpClientRequest request = client.request(HttpMethod.POST, "/bounce", response -> {
      response.bodyHandler(buf -> {
        JsonObject obj = buf.toJsonObject();
        if (obj.containsKey("scored")) {
          // Do not update position, Stadium will do it
          isSticked = false;
          dx = dy = 0;
          handler.handle(true);
        } else if (obj.containsKey("x")) {
          // Contains bounce data
          x = obj.getDouble("x");
          y = obj.getDouble("y");
          double normDx = obj.getDouble("dx");
          double normDy = obj.getDouble("dy");
          dx = normDx * newSpeed;
          dy = normDy * newSpeed;
          handler.handle(true);
        } else {
          handler.handle(false);
        }
      });
    });

    // Register game object
    String json = new JsonObject()
        .put("xStart", oldX)
        .put("yStart", oldY)
        .put("xEnd", newX)
        .put("yEnd", newY)
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
  }

  private void display() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
    HttpClientRequest request = client.request(HttpMethod.POST, "/movegameobject", response -> {});

    // Register game object
    String json = new JsonObject()
        .put("id", "ball")
        .put("x", x)
        .put("y", y)
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
  }

  private static double vecSize(double x, double y) {
    return Math.sqrt(x*x+y*y);
  }
}
