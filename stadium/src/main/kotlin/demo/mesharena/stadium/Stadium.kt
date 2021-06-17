package demo.mesharena.stadium

import demo.mesharena.common.*
import demo.mesharena.common.Commons.BALL_HOST
import demo.mesharena.common.Commons.BALL_PORT
import demo.mesharena.common.Commons.STADIUM_PORT
import demo.mesharena.common.Commons.getDoubleEnv
import demo.mesharena.common.Commons.getIntEnv
import demo.mesharena.common.Commons.getStringEnv
import io.opentracing.Tracer
import io.vertx.core.AsyncResult
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.micrometer.PrometheusScrapingHandler
import java.security.SecureRandom
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class Stadium(private val client: WebClient, private val tracer: Tracer?, private val displayer: Displayer) : CoroutineVerticle() {
  private val locals = getStringEnv("STADIUM_LOCALS", "Locals")
  private val visitors = getStringEnv("STADIUM_VISITORS", "Visitors")
  private val name = getStringEnv("STADIUM_NAME", "stadium")
  private val scale = getDoubleEnv("STADIUM_SCALE", 1.0)
  private val txTop = getIntEnv("STADIUM_TOP", 50)
  private val txLeft = getIntEnv("STADIUM_LEFT", 20)
  private val top = txTop + (47 * scale).toInt()
  private val left = txLeft + (63 * scale).toInt()
  private val txWidth = (490 * scale).toInt()
  private val txHeight = (700 * scale).toInt()
  private val width = (363 * scale).toInt()
  private val height = (605 * scale).toInt()
  private val goalSize = (54 * scale).toInt()
  private val matchTime = 1000 * getIntEnv("STADIUM_MATCH_TIME", 60 * 2)
  private val goalA = Segment(Point((left + width / 2 - goalSize / 2).toDouble(), top.toDouble()), Point((left + width / 2 + goalSize / 2).toDouble(), top.toDouble()))
  private val goalB = Segment(Point((left + width / 2 - goalSize / 2).toDouble(), (top + height).toDouble()), Point((left + width / 2 + goalSize / 2).toDouble(), (top + height).toDouble()))
  private val arenaSegments = arrayOf(
          Segment(Point(left.toDouble(), top.toDouble()), Point((left + width).toDouble(), top.toDouble())),
          Segment(Point((left + width).toDouble(), top.toDouble()), Point((left + width).toDouble(), (top + height).toDouble())),
          Segment(Point((left + width).toDouble(), (top + height).toDouble()), Point(left.toDouble(), (top + height).toDouble())),
          Segment(Point(left.toDouble(), (top + height).toDouble()), Point(left.toDouble(), top.toDouble()))
  )

  private val rnd = SecureRandom()
  private val stadiumGO = GameObject(
          id = "$name-stadium",
          style = "position: absolute; top: ${txTop}px; left: ${txLeft}px; width: ${txWidth}px; height: ${txHeight}px; background-image: url(./football-ground.png); background-size: cover; color: black"
  )
  private val scoreGO = GameObject(
          id = "$name-score",
          style = "position: absolute; top: ${txTop + 5}px; left: ${txLeft + 5}px; color: black; font-weight: bold; z-index: 10;"
  )

  private var scoreA = 0
  private var scoreB = 0
  private var startTime = System.currentTimeMillis()

  override suspend fun start() {
    // Register stadium API
    val serverOptions = HttpServerOptions().setPort(STADIUM_PORT)
    val router = Router.router(vertx)

    if (Commons.METRICS_ENABLED == 1) {
      router.route("/metrics").handler(PrometheusScrapingHandler.create())
    }

    router["/health"].handler { it.response().end() }
    router["/centerBall"].handler { startGame(it) }
    router["/randomBall"].handler { randomBall(it) }
    router.post("/bounce").handler { bounce(it) }
    router["/info"].handler { info(it) }

    vertx.createHttpServer().requestHandler(router)
            .listen(serverOptions.port, serverOptions.host)

    // Ping-display
    vertx.setPeriodic(2000) { display() }
  }

  private fun startGame(ctx: RoutingContext) {
    scoreB = 0
    scoreA = 0
    startTime = System.currentTimeMillis()
    vertx.setPeriodic(1000) { loopId ->
      if (System.currentTimeMillis() - startTime >= matchTime) {
        // End of game!
        vertx.cancelTimer(loopId)
      }
      display()
    }
    resetBall()
    ctx.response().end()
  }

  private fun randomBall(ctx: RoutingContext) {
    val json = JsonObject()
            .put("x", left + rnd.nextInt(width))
            .put("y", top + rnd.nextInt(height))
    val request = client.put(BALL_PORT, BALL_HOST, "/setPosition")
    request.timeout(1000).sendJson(json) {
      if (!it.succeeded()) {
        it.cause().printStackTrace()
      }
    }
    ctx.response().end()
  }

  private fun resetBall() {
    val json = JsonObject()
            .put("x", left + width / 2)
            .put("y", top + height / 2)
    val request = client.put(BALL_PORT, BALL_HOST, "/setPosition")
    request.timeout(1000).sendJson(json) { ar: AsyncResult<HttpResponse<Buffer?>?> ->
      if (!ar.succeeded()) {
        ar.cause().printStackTrace()
      }
    }
  }

  private fun bounce(ctx: RoutingContext) {
    ctx.request().bodyHandler {
      val json = it.toJsonObject()
      val rs = bounce(ctx, json.mapTo(StadiumBounce.Rq::class.java).seg, -1)
      ctx.response().end(rs.jsonString())
    }
  }

  private fun bounce(ctx: RoutingContext, segment: Segment, excludeWall: Int): StadiumBounce.Rs {
    if (isOutside(segment.start)) {
      return StadiumBounce.noBounced()
    }
    val goalA = goalA.getCrossingPoint(segment)
    if (goalA != null) { // Team B scored!
      scoreB++
      resetBall()
      return StadiumBounce.scored("visitors")
    }
    val goalB = goalB.getCrossingPoint(segment)
    if (goalB != null) { // Team A scored!
      scoreA++
      resetBall()
      return StadiumBounce.scored("locals")
    }
    var minDistance = -1.0
    var collision: Point? = null
    var bounceWall = -1
    for (i in arenaSegments.indices) {
      if (i == excludeWall) {
        continue
      }
      val wall = arenaSegments[i]
      val tempCollision = wall.getCrossingPoint(segment)
      if (tempCollision != null) {
        val dist = tempCollision.diff(segment.start).size()
        // minDistance is used to keep only the first wall encountered; if any wall was encounted first, forget this one.
        if (minDistance < 0 || dist < minDistance) {
          minDistance = dist
          collision = tempCollision
          bounceWall = i
        }
      }
    }
    if (collision != null) {
      // Calculate bouncing vector and position of resulting position
      val (dx, dy) = segment.end.diff(collision).normalize()
      val (px, py) = arenaSegments[bounceWall].start.diff(collision).normalize()
      var dAngle = acos(dx)
      if (dy > 0) {
        dAngle = 2 * Math.PI - dAngle
      }
      var pAngle = acos(px)
      if (py > 0) {
        pAngle = 2 * Math.PI - pAngle
      }
      var dpAngle = 2 * pAngle - dAngle
      while (dpAngle >= 2 * Math.PI) {
        dpAngle -= 2 * Math.PI
      }
      val newPos = collision.add(Point(cos(dpAngle), -sin(dpAngle)).mult(segment.size() - minDistance))
      val resultVector = Segment(collision, newPos)
      // Recursive call to check if the result vector itself is bouncing again
      val nextBounce = bounce(ctx, resultVector, bounceWall)
      if (nextBounce.didBounce()) {
        // Bounced again in recursive call => return it
        return nextBounce
      }
      return StadiumBounce.collided(newPos, resultVector.derivate().normalize())
    }
    return StadiumBounce.noBounced()
  }

  private fun isOutside(pos: Point): Boolean {
    return pos.x < left || pos.x > left + width || pos.y < top || pos.y > top + height
  }

  private fun info(ctx: RoutingContext) {
    ctx.request().bodyHandler {
      val input = it.toJsonObject()
      val isVisitors = input.getBoolean("isVisitors")
      val oppGoal: Point = (if (isVisitors) goalA else goalB).middle()
      val ownGoal: Point = (if (isVisitors) goalB else goalA).middle()
      val direction = oppGoal.diff(ownGoal).normalize()
      val (tl, br) = getDefendZone(ownGoal, direction)
      val arenaInfo = ArenaInfo(tl, br, oppGoal)
      ctx.response().end(JsonObject.mapFrom(arenaInfo).toString())
    }
  }

  private fun getDefendZone(goalMiddle: Point, txUnitVec: Point): Segment {
    val boxHalfSize: Double = min(width, height) / 4.toDouble()
    val topLeft = Point(-boxHalfSize, -boxHalfSize)
    val bottomRight = Point(boxHalfSize, boxHalfSize)
    return Segment(topLeft, bottomRight)
            .add(goalMiddle)
            .add(txUnitVec.mult(boxHalfSize))
  }

  private fun getScoreText(): String {
    val elapsed = min(matchTime, (System.currentTimeMillis() - startTime).toInt() / 1000)
    val minutes = elapsed / 60
    val seconds = elapsed % 60
    val text = "$locals: $scoreA - $visitors: $scoreB ~~ Time: ${adjust(minutes)}:${adjust(seconds)}"
    return if (elapsed == matchTime) {
      if (scoreA == scoreB) {
        "$text ~~ Draw game!"
      } else text + " ~~ " + (if (scoreA > scoreB) locals else visitors) + " win!"
    } else text
  }

  private fun adjust(number: Int): String {
    return if (number < 10) "0$number" else number.toString()
  }

  private fun display() {
    displayer.send(stadiumGO)
    scoreGO.text = name + " - " + getScoreText()
    displayer.send(scoreGO)
  }
}
