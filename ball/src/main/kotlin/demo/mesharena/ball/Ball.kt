package demo.mesharena.ball

import demo.mesharena.common.*
import demo.mesharena.common.Commons.BALL_PORT
import demo.mesharena.common.Commons.STADIUM_HOST
import demo.mesharena.common.Commons.STADIUM_PORT
import demo.mesharena.common.Commons.getIntEnv
import demo.mesharena.common.Commons.getStringEnv
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.micrometer.PrometheusScrapingHandler
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.tracing.opentracing.OpenTracingUtil
import java.security.SecureRandom
import java.util.*
import kotlin.math.max
import kotlin.math.min

class Ball(private val client: WebClient, private val tracer: Tracer?, private val displayer: Displayer) : CoroutineVerticle() {
  private val zero = Point(0.0, 0.0)
  private val deltaMs = 200L
  private val resistance = getIntEnv("RESISTANCE", 80).toDouble()
  private val pctErrors = getIntEnv("PCT_ERRORS", 0).toDouble()
  private val image = getStringEnv("IMAGE", "ball")

  private val rnd = SecureRandom()
  private val ballGO = GameObject(
    id = "ball-" + UUID.randomUUID().toString(),
    style = "position: absolute; background-image: url(./$image.png); width: 20px; height: 20px;"
      + "z-index: 5; transition: top ${deltaMs}ms, left ${deltaMs}ms;"
  )
  private val registry: MeterRegistry? = BackendRegistries.getDefaultNow()
  private var speed = zero
  private var controllingPlayer: BallControl.Player? = null
  private var controllingPlayerSkillTimer = 0.0
  private var pos = Point(50.0, 50.0)
  private var interactTimer = 0.0
  private var errorTimer = 0.0

  private var shootSpan: Span? = null
  private var lastShootRef: SpanContext? = null

  override suspend fun start() {
    if (registry != null) {
      Gauge.builder("mesharena_ball_speed") { speed.size() }
        .description("Ball speed gauge")
        .register(registry)
    }

    // Register stadium API
    val serverOptions = HttpServerOptions().setPort(BALL_PORT)
    val router = Router.router(vertx)

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create())
    }

    router["/health"].handler { it.response().end() }
    router["/hasControl"].handler { hasControl(it) }
    router.put("/shoot").handler { shoot(it) }
    router.put("/setPosition").handler { setPosition(it) }
    vertx.createHttpServer().requestHandler(router)
      .listen(serverOptions.port, serverOptions.host)

    // Ping-display
    vertx.setPeriodic(2000) { display() }

    // Start game loop
    vertx.setPeriodic(deltaMs) { update(deltaMs.toDouble() / 1000.0) }
  }

  private fun hasControl(ctx: RoutingContext) {
    ctx.request().bodyHandler {
      if (rnd.nextInt(100) < pctErrors) {
        errorTimer = 0.0
        ctx.response().setStatusCode(503).setStatusMessage("faiiiiilure! (to test outlier detection)").end()
        return@bodyHandler
      }
      interactTimer = 0.0
      val rq = it.toJsonObject().mapTo(BallControl.Rq::class.java)
      val distanceToBall = pos.diff(rq.pos).size()
      val curPlayer = controllingPlayer
      val rs = when {
        distanceToBall < 15 -> when {
          rq.player.id == curPlayer?.id -> {
            curPlayer.skill = rq.player.skill
            BallControl.ballKept(pos)
          }
          rq.player.takesBall(curPlayer, rnd) -> {
            controllingPlayer = rq.player
            if (registry != null) {
              Counter.builder("mesharena_take_ball")
                .description("Counter of player taking control of the ball")
                .tag("team", rq.player.team)
                .tag("player", rq.player.name)
                .register(registry)
                .increment()
            }
            BallControl.ballTaken(pos)
          }
          else -> BallControl.ballMissed(pos)
        }
        else -> BallControl.ballMissed(pos)
      }
      ctx.response().end(rs.jsonString())
    }
  }

  private fun shoot(ctx: RoutingContext) {
    ctx.request().bodyHandler {
      val rq = it.toJsonObject().mapTo(BallShoot.Rq::class.java)
      val curPlayer = controllingPlayer
      if (curPlayer?.id == rq.playerID) {
        shootSpan?.finish()
        shootSpan = tracer?.buildSpan("Ball shot")
          ?.withTag("team", curPlayer.team)
          ?.withTag("player", curPlayer.name)
          ?.asChildOf(OpenTracingUtil.getSpan())
          ?.start()
        lastShootRef = shootSpan?.context()
        speed = rq.vec
        if ("togoal" == rq.kind) {
          if (rnd.nextInt(2) == 0) {
            comment("${curPlayer.name} shooting!")
          } else {
            comment("Wooow ${curPlayer.name} tries his luck!")
          }
          if (registry != null) {
            Counter.builder("mesharena_shoots")
              .description("Shoots counter")
              .tag("team", curPlayer.team)
              .tag("player", curPlayer.name)
              .register(registry)
              .increment()
          }
        } else if ("forward" == rq.kind) {
          if (rnd.nextInt(2) == 0) {
            comment("Still ${curPlayer.name}...")
          } else {
            comment("${curPlayer.name} again...")
          }
        } else if ("defensive" == rq.kind) {
          if (rnd.nextInt(2) == 0) {
            comment("Defensive shooting from ${curPlayer.name}")
          } else {
            comment("${curPlayer.name} takes the ball and shoots!")
          }
        } else if ("control" == rq.kind) {
          comment("${curPlayer.name} takes the ball back")
        }
      }
      ctx.response().end()
    }
  }

  private fun comment(text: String) {
    displayer.send(GameObject(
      id = "ball-comment",
      style = "position: absolute; color: brown; font-weight: bold; z-index: 10; top: ${pos.y + 10}px; left: ${pos.x - 10}px;",
      text = text
    ))
  }

  private fun setPosition(ctx: RoutingContext) {
    ctx.request().bodyHandler {
      val json = it.toJsonObject()
      val x = json.getDouble("x")
      val y = json.getDouble("y")
      pos = Point(x, y)
      speed = zero
      controllingPlayer = null
      ctx.response().end()
      display()
    }
  }

  private fun update(delta: Double) {
    val oldSpeed = speed.size()
    if (oldSpeed > 0) {
      val oldPos = pos
      val newPos = pos.add(speed.mult(delta))
      val newSpeed = max(0.0, oldSpeed - resistance * delta)
      checkBounce(Segment(pos, newPos), newSpeed) { didBounce ->
        if (!didBounce) {
          pos = newPos
          speed = speed.mult(newSpeed / oldSpeed)
        }
        if (oldPos != pos) {
          display()
        }
      }
    } else { // Shoot finished
      shootSpan?.finish()
      shootSpan = null
      lastShootRef = null
    }
    // Decrease controlling skill
    val curPlayer = controllingPlayer
    if (curPlayer != null && curPlayer.skill > 0) {
      controllingPlayerSkillTimer += delta
      if (controllingPlayerSkillTimer >= 0.5) {
        curPlayer.skill--
        controllingPlayerSkillTimer = 0.0
      }
    }
    interactTimer += delta
    errorTimer += delta
  }

  private fun checkBounce(seg: Segment, newSpeed: Double, handler: (Boolean) -> Unit) {
    val request = client.post(STADIUM_PORT, STADIUM_HOST, "/bounce")
    val checkSpan = tracer?.buildSpan("Check bounce")
      ?.asChildOf(lastShootRef)
      ?.start()
    OpenTracingUtil.setSpan(checkSpan)
    lastShootRef = checkSpan?.context()
    val curPlayer = controllingPlayer ?: BallControl.Player("?", 0, "?", "?")
    request.sendJson(StadiumBounce.Rq(seg)) {
      checkSpan?.finish()
      if (!it.succeeded()) {
        // No stadium => no bounce
        handler(false)
      } else {
        val bounce = it.result().bodyAsJsonObject().mapTo(StadiumBounce.Rs::class.java)
        val collision = bounce.collision
        if (bounce.scoredTeam != null) {
          val isOwn = bounce.scoredTeam != curPlayer.team
          if (isOwn) {
            comment("Ohhhh own goal from ${curPlayer.name} !!")
          } else {
            comment("Goaaaaaaal by ${curPlayer.name} !!!!")
          }
          if (registry != null) {
            Counter.builder("mesharena_goals")
              .description("Goals counter")
              .tag("team", curPlayer.team)
              .tag("player", curPlayer.name)
              .tag("own_goal", if (isOwn) "yes" else "no")
              .register(registry)
              .increment()
          }
          // Span update
          val goalSpan = tracer?.buildSpan("Goal!")
            ?.withTag("team", curPlayer.team)
            ?.withTag("player", curPlayer.name)
            ?.withTag("own_goal", if (isOwn) "yes" else "no")
            ?.asChildOf(lastShootRef)
          goalSpan?.start()?.finish()
          shootSpan?.finish()
          shootSpan = null
          lastShootRef = null
          // Do not update position, Stadium will do it
          controllingPlayer = null
          speed = zero
          handler(true)
        } else if (collision != null) {
          pos = collision.pos
          speed = collision.vec.mult(newSpeed)
          // Span update
          val bounceSpan = tracer?.buildSpan("Bounce")
            ?.withTag("x", pos.x)
            ?.withTag("y", pos.y)
            ?.asChildOf(lastShootRef)
            ?.start()
          lastShootRef = bounceSpan?.context()
          bounceSpan?.finish()
          handler(true)
        } else {
          handler(false)
        }
      }
    }
  }

  private fun display() {
    ballGO.x = pos.x - 10
    ballGO.y = pos.y - 10
    ballGO.style = getStyle()
    displayer.send(ballGO)
  }

  private fun getStyle(): String {
    val filters: String
    filters = if (errorTimer < 3.0) {
      // filter: brightness(40%) sepia(100%) hue-rotate(-50deg) saturate(600%);
      // error ]0 (no err), 1 (err)]
      val error = 1.0 - errorTimer / 3.0
      // hue rotate ]0 (no err), -50 (err)]
      val hue = (-50 * error).toInt()
      // brightness [40 (err), 100 (no err)[
      val brightness = 40 + (60.0 * (1.0 - error)).toInt()
      // sepia [0 (no err), 100 (err)[
      val sepia = (100.0 * error).toInt()
      // saturate ]100 (no err), 600 (err)]
      val saturate = 100 + (500.0 * error).toInt()
      ("filter: brightness($brightness%) sepia($sepia%) hue-rotate(${hue}deg) saturate($saturate%);"
        + "-webkit-filter: brightness($brightness%) sepia($sepia%) hue-rotate(${hue}deg) saturate($saturate%);")
    } else {
      // interact [0 (old), 1 (now)]
      val interact = 1.0 - min(interactTimer, 3.0) / 3.0
      // brightness [40 (old), 100 (now)]
      val brightness = 40 + (60.0 * interact).toInt()
      "filter: brightness($brightness%); -webkit-filter: brightness($brightness%);"
    }
    return ("position: absolute; background-image: url(./$image.png); width: 20px; height: 20px;"
      + "z-index: 5; transition: top ${deltaMs}ms, left ${deltaMs}ms;"
      + filters)
  }
}
