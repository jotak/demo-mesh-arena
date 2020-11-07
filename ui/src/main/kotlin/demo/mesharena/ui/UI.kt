package demo.mesharena.ui

import demo.mesharena.common.Commons
import demo.mesharena.common.Commons.STADIUM_HOST
import demo.mesharena.common.Commons.STADIUM_PORT
import demo.mesharena.common.GameObject
import io.opentracing.Tracer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.micrometer.PrometheusScrapingHandler
import io.vertx.tracing.opentracing.OpenTracingUtil

class UI(private val tracer: Tracer?) : CoroutineVerticle() {
  private val gameObjects = mutableMapOf<String, ExpiringGameObject>()

  override suspend fun start() {
    val serverOptions = HttpServerOptions().setPort(Commons.UI_PORT)
    val router = Router.router(vertx)

    // Allow events for the designated addresses in/out of the event bus bridge
    val opts = SockJSBridgeOptions()
      .addOutboundPermitted(PermittedOptions().setAddress("displayGameObject"))
      .addOutboundPermitted(PermittedOptions().setAddress("removeGameObject"))
      .addInboundPermitted(PermittedOptions().setAddress("init-session"))
      .addInboundPermitted(PermittedOptions().setAddress("shoot"))
      .addInboundPermitted(PermittedOptions().setAddress("centerBall"))
      .addInboundPermitted(PermittedOptions().setAddress("randomBall"))

    // Create the event bus bridge and add it to the router.
    val sockJSHandler = SockJSHandler.create(vertx)
    sockJSHandler.bridge(opts)
    router.route("/eventbus/*").handler(sockJSHandler)

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create())
    }
    router.get("/health").handler { it.response().end() }

    // Listen to objects creation
    router.post("/display").handler { displayRoute(it) }

    // Create a router endpoint for the static content.
    router.route().handler(StaticHandler.create())

    // Start the web server and tell it to use the router to handle requests.
    vertx.createHttpServer().requestHandler(router).listen(serverOptions.port, serverOptions.host)

    // Init Kafka to receive display events (can be used instead of the REST endpoint)
    val consumer = Commons.kafkaConsumer(vertx, "ui")
    consumer?.subscribe("display")
      ?.onSuccess { _ -> consumer.handler { rec -> display(rec.value()) } }
      ?.onFailure(Throwable::printStackTrace)

    val eb = vertx.eventBus()
    // Send all game objects already registered
    eb.consumer<JsonArray>("init-session") { it.reply(JsonArray(gameObjects.values.map { o -> o.gameObject.json() }.toList())) }

    eb.consumer<JsonObject>("shoot") {
      val json = it.body()
      val span = tracer?.buildSpan("Click shoot")
        ?.withTag("who", json.getString("who"))
        ?.start()
      OpenTracingUtil.setSpan(span)
      WebClient.create(vertx).get(8080, json.getString("ip"), "/tryShoot")
        .send { ar ->
          span?.finish()
          if (!ar.succeeded()) {
            ar.cause().printStackTrace()
          }
        }
    }

    eb.consumer<Any>("centerBall") {
      val span = tracer?.buildSpan("Center ball")?.start()
      OpenTracingUtil.setSpan(span)
      WebClient.create(vertx).get(STADIUM_PORT, STADIUM_HOST, "/centerBall")
        .send { ar ->
          span?.finish()
          if (!ar.succeeded()) {
            ar.cause().printStackTrace()
          }
        }
    }

    eb.consumer<Any>("randomBall") {
      val span = tracer?.buildSpan("Random ball")?.start()
      OpenTracingUtil.setSpan(span)
      WebClient.create(vertx).get(STADIUM_PORT, STADIUM_HOST, "/randomBall")
        .send { ar ->
          span?.finish()
          if (!ar.succeeded()) {
            ar.cause().printStackTrace()
          }
        }
    }

    // Objects timeout
    vertx.setPeriodic(5000) {
      val now = System.currentTimeMillis()
      gameObjects.entries.removeIf {
        if (now - it.value.timestamp > 2000) {
          eb.publish("removeGameObject", it.key)
          true
        } else false
      }
    }
  }

  private fun displayRoute(ctx: RoutingContext) {
    ctx.request().bodyHandler {
      val json = it.toJsonObject()
      ctx.response().end()
      display(json)
    }
  }

  private fun display(data: JsonObject) {
    val gameObj = data.mapTo(GameObject::class.java)
    val old = gameObjects[gameObj.id]
    if (old == null || old.gameObject != gameObj) {
      vertx.eventBus().publish("displayGameObject", data)
    }
    gameObjects[gameObj.id] = ExpiringGameObject(System.currentTimeMillis(), gameObj)
  }

  data class ExpiringGameObject(var timestamp: Long, val gameObject: GameObject)
}
