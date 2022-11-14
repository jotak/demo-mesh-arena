import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.micrometer.PrometheusScrapingHandler;

import java.util.HashMap;
import java.util.Map;

record ExpiringGameObject(long timestamp, GameObject gameObject) {
}

public class UIVerticle extends AbstractVerticle {
  private final Map<String, ExpiringGameObject> gameObjects = new HashMap<>();

  @Override
  public void start() throws Exception {
    System.out.println("start");
    var router = Router.router(vertx);

    // Create the event bus bridge and add it to the router.
    var sockJSHandler = SockJSHandler.create(vertx);
    router.route("/eventbus/*").subRouter(sockJSHandler.bridge(new SockJSBridgeOptions()
      .addOutboundPermitted(new PermittedOptions().setAddress("displayGameObject"))
      .addOutboundPermitted(new PermittedOptions().setAddress("removeGameObject"))
      .addInboundPermitted(new PermittedOptions().setAddress("init-session"))
      .addInboundPermitted(new PermittedOptions().setAddress("shoot"))
      .addInboundPermitted(new PermittedOptions().setAddress("centerBall"))
      .addInboundPermitted(new PermittedOptions().setAddress("randomBall"))
    ));

    if (Commons.METRICS_ENABLED) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create());
    }
    router.get("/health").handler(it -> it.response().end());

    // Listen to objects creation
    router.post("/display").handler(this::displayRoute);

    router.route().handler(StaticHandler.create());

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(Commons.UI_PORT)
      .onSuccess(server ->
        System.out.println("HTTP server started on http://localhost:" + server.actualPort())
      )
      .onFailure(Throwable::printStackTrace);

    var eb = vertx.eventBus();
    // Send all game objects already registered
    eb.consumer("init-session", it -> {
      var objects = gameObjects.values().stream()
        .map(o -> o.gameObject().json())
        .toList();
      it.reply(new JsonArray(objects));
    });

    eb.consumer("shoot", it -> {
      var json = (JsonObject) it.body();
      WebClient.create(vertx).get(8080, json.getString("ip"), "/tryShoot")
        .send(ar -> {
          if (!ar.succeeded()) {
            ar.cause().printStackTrace();
          }
        });
    });

    eb.consumer("centerBall", it -> {
      WebClient.create(vertx).get(Commons.STADIUM_PORT, Commons.STADIUM_HOST, "/centerBall")
        .send(ar -> {
          if (!ar.succeeded()) {
            ar.cause().printStackTrace();
          }
        });
    });

    eb.consumer("randomBall", it -> {
      WebClient.create(vertx).get(Commons.STADIUM_PORT, Commons.STADIUM_HOST, "/randomBall")
        .send(ar -> {
          if (!ar.succeeded()) {
            ar.cause().printStackTrace();
          }
        });
    });

    // Objects timeout
    vertx.setPeriodic(5000, it -> {
      var now = System.currentTimeMillis();
      gameObjects.entrySet().removeIf(entry -> {
        if (now - entry.getValue().timestamp() > 2000) {
          eb.publish("removeGameObject", entry.getKey());
          return true;
        }
        return false;
      });
    });
  }

  private void displayRoute(RoutingContext ctx) {
    ctx.request().bodyHandler(it -> {
      var json = it.toJsonObject();
      ctx.response().end();
      display(json);
    });
  }

  private void display(JsonObject data) {
    var gameObj = data.mapTo(GameObject.class);
    var old = gameObjects.get(gameObj.id());
    if (old == null || !old.gameObject().equals(gameObj)) {
      vertx.eventBus().publish("displayGameObject", data);
    }
    gameObjects.put(gameObj.id(), new ExpiringGameObject(System.currentTimeMillis(), gameObj));
  }

  public static void main(String[] args) {
    Commons.vertx().deployVerticle(new UIVerticle());
  }
}
