package demo.mesharena.ai

import demo.mesharena.common.*
import demo.mesharena.common.Commons.BALL_HOST
import demo.mesharena.common.Commons.BALL_PORT
import demo.mesharena.common.Commons.STADIUM_HOST
import demo.mesharena.common.Commons.STADIUM_PORT
import demo.mesharena.common.Commons.getDoubleEnv
import demo.mesharena.common.Commons.getIntEnv
import demo.mesharena.common.Commons.getStringEnv
import io.opentracing.Span
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.micrometer.PrometheusScrapingHandler
import io.vertx.tracing.opentracing.OpenTracingUtil
import java.security.SecureRandom
import java.util.*
import kotlin.math.min

class AI(private val client: WebClient, private val tracer: Tracer?, private val displayer: Displayer) : CoroutineVerticle() {
  private val deltaMs = 300L
  private val idleTime = 2.0
  private val roleTime = 10.0
  private val names = getStringEnv("PLAYER_NAMES", "Goat,Sheep,Cow,Chicken,Pig,Lamb")
  private val color = getStringEnv("PLAYER_COLOR", "blue")
  private val team = getStringEnv("PLAYER_TEAM", "locals")
  // Speed = open scale
  private val speed = getIntEnv("PLAYER_SPEED", 60).toDouble()
  // Accuracy [0, 1]
  private val accuracy = getDoubleEnv("PLAYER_ACCURACY", 0.8)
  private val minSpeed = accuracy * speed
  // Skill = open scale
  private val skill = getIntEnv("PLAYER_SKILL", 5)
  // Shoot strength = open scale
  private val shootStrength = getIntEnv("PLAYER_SHOOT", 250).toDouble()
  // Attacking / defending? (more is attacking) [0, 100]
  private val attacking = getIntEnv("PLAYER_ATTACKING", 65)
  // While attacking, will shoot quickly? [0, 100]
  private val attShootFast = getIntEnv("PLAYER_ATT_SHOOT_FAST", 20)
  // While defending, will shoot quickly? [0, 100]
  private val defShootFast = getIntEnv("PLAYER_DEF_SHOOT_FAST", 40)
  private val interactiveMode = getIntEnv("INTERACTIVE_MODE", 0) == 1

  private val rnd = SecureRandom()
  private val id = UUID.randomUUID().toString()
  private val name = {
    val names = names.split(",")
    val i = rnd.nextInt(names.size)
    names[i]
  }()
  private val aiGO = GameObject(
    id = id,
    style = "position: absolute; background-color: $color; transition: top ${deltaMs}ms, left ${deltaMs}ms; height: 30px; width: 30px; border-radius: 50%; z-index: 8;",
    playerRef = PlayerRef(name, Commons.getIP())
  )
  private val isVisitors = team != "locals"

  private var pos: Point = Point(0.0, 0.0)
  private var arenaInfo: ArenaInfo? = null
  private var currentDestination: Point? = null
  private var defendPoint: Point? = null
  private var idleTimer = -1.0
  private var roleTimer = -1.0

  private enum class Role { ATTACK, DEFEND }
  private var role: Role = Role.ATTACK

  override suspend fun start() {
    // Register stadium API
    val serverOptions = HttpServerOptions().setPort(8080)
    val router = Router.router(vertx)

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create())
    }

    router["/health"].handler { it.response().end() }
    router["/tryShoot"].handler { tryShoot(it) }

    vertx.createHttpServer().requestHandler(router)
      .listen(serverOptions.port, serverOptions.host)

    // First display
    display()

    // Check regularly about arena info
    checkArenaInfo()
    vertx.setPeriodic(5000) { checkArenaInfo() }

    // Start game loop
    vertx.setPeriodic(deltaMs) { update(deltaMs.toDouble() / 1000.0) }
  }

  private fun checkArenaInfo() {
    client[STADIUM_PORT, STADIUM_HOST, "/info"].sendJson(JsonObject().put("isVisitors", isVisitors)) {
      arenaInfo = if (!it.succeeded()) {
        it.cause().printStackTrace()
        null
      } else {
        it.result().bodyAsJsonObject().mapTo(ArenaInfo::class.java)
      }
    }
  }

  private fun update(delta: Double) {
    if (idleTimer > 0) {
      idleTimer -= delta
      walkRandom(delta)
    } else {
      roleTimer -= delta
      if (roleTimer < 0) {
        roleTimer = roleTime
        chooseRole()
      }
      lookForBall(delta, false, null)
    }
  }

  private fun chooseRole() {
    if (rnd.nextInt(100) > attacking) {
      role = Role.DEFEND
      defendPoint = arenaInfo?.randomDefendPoint(rnd) ?: randomDestination()
    } else {
      role = Role.ATTACK
    }
  }

  private fun randomDestination(): Point {
    return Point(rnd.nextInt(500).toDouble(), rnd.nextInt(500).toDouble())
  }

  private fun walkRandom(delta: Double) {
    if (currentDestination == null || Segment(pos, currentDestination!!).size() < 10) {
      currentDestination = randomDestination()
    }
    walkToDestination(delta)
  }

  private fun walkToDestination(delta: Double) {
    if (currentDestination != null) {
      // Speed and angle are modified by accuracy
      val segToDest = Segment(pos, currentDestination!!)
      // maxSpeed avoids stepping to high when close to destination
      val maxSpeed = min(segToDest.size() / delta, speed)
      // minSpeed must be kept <= maxSpeed
      val minSpeed = min(maxSpeed, minSpeed)
      val speed = delta * (minSpeed + rnd.nextDouble() * (maxSpeed - minSpeed))
      val relativeMove: Point = randomishSegmentNormalized(segToDest).mult(speed)
      pos = pos.add(relativeMove)
      display()
    }
  }

  private fun randomishSegmentNormalized(segToDest: Segment): Point {
    var angle: Double = rnd.nextDouble() * (1.0 - accuracy) * Math.PI
    if (rnd.nextInt(2) == 0) {
      angle *= -1.0
    }
    return segToDest.derivate().normalize().rotate(angle)
  }

  private fun display() {
    aiGO.x = pos.x - 15
    aiGO.y = pos.y - 15
    displayer.send(aiGO)
  }

  private fun tryShoot(ctx: RoutingContext) {
    lookForBall(0.0, true, OpenTracingUtil.getSpan())
    ctx.response().end()
  }

  private fun lookForBall(delta: Double, isHumanShot: Boolean, currentSpan: Span?) {
    val rq = BallControl.Rq(pos, BallControl.Player(id, skill, name, team))
    val request = client[BALL_PORT, BALL_HOST, "/hasControl"]
    val ballControlSpan = tracer?.buildSpan("Ball control")
      ?.withTag("name", name)
      ?.withTag("id", id)
      ?.asChildOf(currentSpan)
      ?.start()
    OpenTracingUtil.setSpan(ballControlSpan)
    request.sendJson(rq)
      .map {
        if (it.statusCode() == 200) {
          return@map it.bodyAsJsonObject()
        }
        null
      }
      .onComplete {
        ballControlSpan?.finish()
        val rs = it.result()?.mapTo(BallControl.Rs::class.java)
        if (!it.succeeded() || rs == null) {
          if (!isHumanShot) {
            // No ball? What a pity. Walk randomly in sadness.
            idle()
          }
        } else {
          if (!isHumanShot) {
            val ball = rs.pos
            currentDestination = if (role == Role.ATTACK || pos.diff(ball).size() < 100) ball else defendPoint
          }
          if (rs.success && (isHumanShot || !interactiveMode)) {
            // Run on context, so that active span that is set in "shoot" doesn't gets erased too early upon "hasControl" response processed
            vertx.runOnContext { shoot(rs.takesBall, ballControlSpan?.context()) }
          }
          if (!isHumanShot) {
            walkToDestination(delta)
          }
        }
      }
  }

  private fun idle() {
    idleTimer = idleTime
  }

  private fun shootVec(direction: Point, baseStrength: Double): Point {
    // From 50% to 100% of base strength
    val strength = baseStrength * (0.5 + rnd.nextDouble() * 0.5)
    return direction.mult(strength)
  }

  private fun shoot(takesBall: Boolean, spanContext: SpanContext?) {
    val shootVector: Point
    val kind: String
    var direction = randomishSegmentNormalized(
      Segment(pos, arenaInfo?.goal ?: randomDestination()))
    if (role == Role.ATTACK) {
      // Go forward or try to shoot
      val rndNum = rnd.nextInt(100)
      if (rndNum < attShootFast) {
        // Try to shoot (if close enough to ball)
        shootVector = shootVec(direction, shootStrength)
        kind = "togoal"
      } else {
        // Go forward
        shootVector = shootVec(direction, speed * 1.8)
        kind = if (takesBall) "control" else "forward"
      }
    } else {
      // Defensive shoot
      // Go forward or defensive shoot
      val rndNum = rnd.nextInt(100)
      if (rndNum < defShootFast) {
        // Defensive shoot, randomise a second time, shoot stronger
        direction = randomishSegmentNormalized(Segment(pos, pos.add(direction)))
        shootVector = shootVec(direction, shootStrength * 1.5)
        kind = "defensive"
      } else {
        // Go forward
        shootVector = shootVec(direction, speed * 1.8)
        kind = if (takesBall) "control" else "forward"
      }
    }
    val rq = BallShoot.Rq(shootVector, kind, id)
    val request = client.put(BALL_PORT, BALL_HOST, "/shoot")
    val shootSpan = tracer?.buildSpan("Player shoot")
      ?.withTag("name", name)
      ?.withTag("id", id)
      ?.withTag("kind", kind)
      ?.withTag("strength", shootVector.size())
      ?.asChildOf(spanContext)
      ?.start()
    OpenTracingUtil.setSpan(shootSpan)
    request.sendJson(JsonObject.mapFrom(rq)) { shootSpan?.finish() }
  }
}
