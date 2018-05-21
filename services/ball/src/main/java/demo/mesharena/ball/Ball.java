package demo.mesharena.ball;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class Ball extends AbstractVerticle {

  private double x;
  private double y;
  private double dx;
  private double dy;

  private Ball() {
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new Ball());
  }

  @Override
  public void start() throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
    HttpClientRequest request = client.request(HttpMethod.POST, "/creategameobject", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

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
    router.put("/set").handler(this::set);
    vertx.createHttpServer().requestHandler(router::accept).listen(8081);

    // Start game loop
    vertx.setPeriodic(200, loopId -> this.update(0.2));
  }

  private void set(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      System.out.println(json);
      x = json.getInteger("x");
      y = json.getInteger("y");
      dx = dy = 0;
      ctx.response().end();
      display();
    });
  }

  private void shoot(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      System.out.println(json);
      dx = json.getInteger("dx");
      dy = json.getInteger("dy");
      ctx.response().end();
    });
  }

  private void update(double delta) {
    x += delta * dx;
    y += delta * dy;
    display();
    // TODO: resistance (decrease absolute derivate)
  }

  private void display() {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
    HttpClientRequest request = client.request(HttpMethod.POST, "/movegameobject", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

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
}
