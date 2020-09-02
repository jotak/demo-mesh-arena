package demo.mesharena.ui;

import demo.mesharena.common.Commons;
import demo.mesharena.common.TracingHeaders;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.micrometer.PrometheusScrapingHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static demo.mesharena.common.Commons.*;

public class UI extends AbstractVerticle {

  private static final Optional<Tracer> TRACER = createTracerFromEnv();
  private final Map<String, GameObject> gameObjects = new HashMap<>();

  private UI() {
  }

  public static void main(String[] args) {
    Commons.vertx(TRACER).deployVerticle(new UI());
  }

  @Override
  public void start() throws Exception {
    HttpServerOptions serverOptions = new HttpServerOptions().setPort(Commons.UI_PORT);

    Router router = Router.router(vertx);

    // Allow events for the designated addresses in/out of the event bus bridge
    SockJSBridgeOptions opts = new SockJSBridgeOptions()
        .addOutboundPermitted(new PermittedOptions().setAddress("displayGameObject"))
        .addOutboundPermitted(new PermittedOptions().setAddress("removeGameObject"))
        .addInboundPermitted(new PermittedOptions().setAddress("init-session"))
        .addInboundPermitted(new PermittedOptions().setAddress("centerBall"))
        .addInboundPermitted(new PermittedOptions().setAddress("randomBall"));

    // Create the event bus bridge and add it to the router.
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
    sockJSHandler.bridge(opts);
    router.route("/eventbus/*").handler(sockJSHandler);

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create());
    }
    router.get("/health").handler(ctx -> ctx.response().end());

    // Listen to objects creation
    router.post("/display").handler(this::displayRoute);

    // Create a router endpoint for the static content.
    router.route().handler(StaticHandler.create());

    // Start the web server and tell it to use the router to handle requests.
    vertx.createHttpServer().requestHandler(router)
        .listen(serverOptions.getPort(), serverOptions.getHost());

    // Init Kafka to receive display events (can be used instead of the REST endpoint)
    Commons.kafkaConsumer(vertx, "ui").ifPresent(c -> {
      c.subscribe("display").onSuccess(v -> c.handler(rec -> display(rec.value())))
        .onFailure(Throwable::printStackTrace);
    });

    EventBus eb = vertx.eventBus();
    eb.consumer("init-session", msg -> {
      // Send all game objects already registered
      List<JsonObject> objects = gameObjects.entrySet().stream().map(entry -> {
        JsonObject json = new JsonObject()
            .put("id", entry.getKey())
            .put("style", entry.getValue().style)
            .put("text", entry.getValue().text);
        if (entry.getValue().x != null) {
          json.put("x", entry.getValue().x)
              .put("y", entry.getValue().y);
        }
        return json;
      }).collect(Collectors.toList());
      msg.reply(new JsonArray(objects));
    });
    eb.consumer("centerBall", msg -> {
      Optional<Span> span = TRACER.map(tracer -> tracer.buildSpan("centerBall").start());
      HttpRequest<Buffer> request = WebClient.create(vertx)
          .get(STADIUM_PORT, STADIUM_HOST, "/centerBall");
      span.ifPresent(s -> TracingHeaders.inject(TRACER.get(), s.context(), request.headers()));
      request.send(ar -> {
        span.ifPresent(Span::finish);
        if (!ar.succeeded()) {
          ar.cause().printStackTrace();
        }
      });
    });
    eb.consumer("randomBall", msg -> {
      Optional<Span> span = TRACER.map(tracer -> tracer.buildSpan("randomBall").start());
      HttpRequest<Buffer> request = WebClient.create(vertx)
          .get(STADIUM_PORT, STADIUM_HOST, "/randomBall");
      span.ifPresent(s -> TracingHeaders.inject(TRACER.get(), s.context(), request.headers()));
      request.send(ar -> {
        span.ifPresent(Span::finish);
        if (!ar.succeeded()) {
          ar.cause().printStackTrace();
        }
      });
    });

    // Objects timeout
    vertx.setPeriodic(5000, loopId -> {
      long now = System.currentTimeMillis();
      gameObjects.entrySet().removeIf(entry -> {
        if (now - entry.getValue().lastCheck > 2000) {
          eb.publish("removeGameObject", entry.getKey());
          return true;
        }
        return false;
      });
    });
  }

  private void displayRoute(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      ctx.response().end();
      display(json);
    });
  }

  private void display(JsonObject data) {
    gameObjects.compute(data.getString("id"), (key, go) -> {
      GameObject out = (go == null) ? new GameObject() : go;
      boolean changed = out.mergeWithJson(data);
      if (changed) {
        vertx.eventBus().publish("displayGameObject", data);
      }
      return out;
    });
  }

  private static class GameObject {
    private String style;
    private String text;
    private Double x;
    private Double y;
    private long lastCheck;

    GameObject() {
    }

    boolean mergeWithJson(JsonObject json) {
      lastCheck = System.currentTimeMillis();
      boolean changed = false;
      if (json.containsKey("style")) {
        String style = json.getString("style");
        changed = !style.equals(this.style);
        this.style = style;
      }
      if (json.containsKey("text")) {
        String text = json.getString("text");
        changed |= !text.equals(this.text);
        this.text = text;
      }
      if (json.containsKey("x")) {
        Double x = json.getDouble("x");
        changed |= !x.equals(this.x);
        this.x = x;
      }
      if (json.containsKey("y")) {
        Double y = json.getDouble("y");
        changed |= !y.equals(this.y);
        this.y = y;
      }
      return changed;
    }
  }
}
