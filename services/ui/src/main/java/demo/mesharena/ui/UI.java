package demo.mesharena.ui;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

public class UI extends AbstractVerticle {

  private UI() {
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new UI());
  }

  @Override
  public void start() throws Exception {
    Router router = Router.router(vertx);

    // Allow events for the designated addresses in/out of the event bus bridge
    BridgeOptions opts = new BridgeOptions()
        .addOutboundPermitted(new PermittedOptions().setAddress("creategameobject"))
        .addOutboundPermitted(new PermittedOptions().setAddress("movegameobject"))
        .addOutboundPermitted(new PermittedOptions().setAddress("textgameobject"))
        .addInboundPermitted(new PermittedOptions().setAddress("init-session"));

    // Create the event bus bridge and add it to the router.
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
    sockJSHandler.bridge(opts);
    router.route("/eventbus/*").handler(sockJSHandler);

    // TODO: replace http API with eventbus messages
    // Listen to objects creation
    router.post("/creategameobject").handler(this::createGameObject);
    router.post("/movegameobject").handler(this::moveGameObject);
    router.post("/textgameobject").handler(this::textGameObject);

    // Create a router endpoint for the static content.
    router.route().handler(StaticHandler.create());

    // Start the web server and tell it to use the router to handle requests.
    vertx.createHttpServer().requestHandler(router::accept).listen(8080);

    EventBus eb = vertx.eventBus();
    eb.consumer("init-session", msg -> {
    });
  }

  private void createGameObject(RoutingContext ctx) {
    System.out.println("Create game object:");
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      System.out.println(json);
      vertx.eventBus().send("creategameobject", json);
      ctx.response().end();
    });
  }

  private void moveGameObject(RoutingContext ctx) {
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      vertx.eventBus().send("movegameobject", json);
      ctx.response().end();
    });
  }

  private void textGameObject(RoutingContext ctx) {
    System.out.println("text game object");
    ctx.request().bodyHandler(buf -> {
      JsonObject json = buf.toJsonObject();
      vertx.eventBus().send("textgameobject", json);
      ctx.response().end();
    });
  }
}
