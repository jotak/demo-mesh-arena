import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.PrometheusScrapingHandler;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

public class StadiumVerticle extends AbstractVerticle {
    private final String locals = Commons.getStringEnv("STADIUM_LOCALS", "Locals");
    private final String visitors = Commons.getStringEnv("STADIUM_VISITORS", "Visitors");
    private final String name = Commons.getStringEnv("STADIUM_NAME", "stadium");
    private final double scale = Commons.getDoubleEnv("STADIUM_SCALE", 1.0);
    private final int txTop = Commons.getIntEnv("STADIUM_TOP", 50);
    private final int txLeft = Commons.getIntEnv("STADIUM_LEFT", 20);
    private final int top = txTop + (int)(47 * scale);
    private final int left = txLeft + (int)(63 * scale);
    private final int txWidth = (int)(490 * scale);
    private final int txHeight = (int)(700 * scale);
    private final int width = (int)(363 * scale);
    private final int height = (int)(605 * scale);
    private final int goalSize = (int)(54 * scale);
    private final int matchTime = 1000 * Commons.getIntEnv("STADIUM_MATCH_TIME", 60 * 2);
    private final Segment goalA = new Segment(new Point(left + width / 2.0 - goalSize / 2.0, top), new Point(left + width / 2.0 + goalSize / 2.0, top));
    private final Segment goalB = new Segment(new Point(left + width / 2.0 - goalSize / 2.0, top + height), new Point(left + width / 2.0 + goalSize / 2.0, top + height));
    private final List<Segment> arenaSegments = List.of(
            new Segment(new Point(left, top), new Point(left + width, top)),
            new Segment(new Point(left + width, top), new Point(left + width, top + height)),
            new Segment(new Point(left + width, top + height), new Point(left, top + height)),
            new Segment(new Point(left, top + height), new Point(left, top))
    );

    private final SecureRandom rnd = new SecureRandom();
    private final GameObject stadiumGO = new GameObject(
            "$name-stadium",
            new Style()
                    .position(txTop, txLeft)
                    .dimensions(txWidth, txHeight)
                    .image("./football-ground.png")
                    .other("background-size: cover; color: black;")
                    .toString(),
            0, 0, null, null
    );
    private final GameObject scoreGO = new GameObject(
            name + "-score",
            new Style()
                    .position(txTop + 5, txLeft + 5)
                    .zIndex(10)
                    .other("color: black; font-weight: bold;")
                    .toString(),
            0, 0, null, null
    );
    private final WebClient client;
    private final Displayer displayer;

    private int scoreA = 0;
    private int scoreB = 0;
    private long startTime = System.currentTimeMillis();

    public StadiumVerticle(WebClient client, Displayer displayer) {
        this.client = client;
        this.displayer = displayer;
    }

    @Override
    public void start() throws Exception {
        System.out.println("start");
        var router = Router.router(vertx);

        if (Commons.METRICS_ENABLED) {
            router.route("/metrics").handler(PrometheusScrapingHandler.create());
        }

        router.route("/health").handler(it -> it.response().end());
        router.route("/centerBall").handler(this::startGame);
        router.route("/randomBall").handler(this::randomBall);
        router.post("/bounce").handler(this::bounce);
        router.route("/info").handler(this::info);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(Commons.STADIUM_PORT)
                .onSuccess(server ->
                    System.out.println("HTTP server started on port " + server.actualPort())
                );

        // Ping-display
        vertx.setPeriodic(2000, a -> display());
    }

    private void startGame(RoutingContext ctx) {
        System.out.println("start game");
        scoreB = 0;
        scoreA = 0;
        startTime = System.currentTimeMillis();
        vertx.setPeriodic(1000, loopId -> {
            if (System.currentTimeMillis() - startTime >= matchTime) {
                // End of game!
                vertx.cancelTimer(loopId);
            }
            display();
        });
        resetBall();
        ctx.response().end();
    }

    private void randomBall(RoutingContext ctx) {
        System.out.println("random ball");
        var json = new JsonObject()
                .put("x", left + rnd.nextInt(width))
                .put("y", top + rnd.nextInt(height));
        var request = client.put(Commons.BALL_PORT, Commons.BALL_HOST, "/setPosition");
        request.timeout(1000).sendJson(json, it -> {
            if (!it.succeeded()) {
                it.cause().printStackTrace();
            }
        });
        ctx.response().end();
    }

    private void resetBall() {
        System.out.println("reset ball");
        var json = new JsonObject()
                .put("x", left + width / 2)
                .put("y", top + height / 2);
        client.put(Commons.BALL_PORT, Commons.BALL_HOST, "/setPosition")
                .timeout(1000).sendJson(json, it -> {
                    if (!it.succeeded()) {
                        it.cause().printStackTrace();
                    }
        });
    }

    private void bounce(RoutingContext ctx) {
        ctx.request().bodyHandler(it -> {
            var json = it.toJsonObject();
            var rs = bounce(json.mapTo(StadiumBounce.Rq.class).seg(), -1);
            ctx.response().end(rs.jsonString());
        });
    }

    private StadiumBounce.Rs bounce(Segment segment, int excludeWall) {
        if (isOutside(segment.start())) {
            return StadiumBounce.noBounced();
        }
        var pGoalA = goalA.getCrossingPoint(segment);
        if (pGoalA.isPresent()) { // Team B scored!
            scoreB++;
            resetBall();
            return StadiumBounce.scored("visitors");
        }
        var pGoalB = goalB.getCrossingPoint(segment);
        if (pGoalB.isPresent()) { // Team A scored!
            scoreA++;
            resetBall();
            return StadiumBounce.scored("locals");
        }
        var minDistance = -1.0;
        Optional<Point> collision = Optional.empty();
        var bounceWall = -1;
        for (int i = 0; i < arenaSegments.size(); i++) {
            if (i == excludeWall) {
                continue;
            }
            var wall = arenaSegments.get(i);
            var tempCollision = wall.getCrossingPoint(segment);
            if (tempCollision.isPresent()) {
                var dist = tempCollision.get().diff(segment.start()).size();
                // minDistance is used to keep only the first wall encountered; if any wall was encounted first, forget this one.
                if (minDistance < 0 || dist < minDistance) {
                    minDistance = dist;
                    collision = tempCollision;
                    bounceWall = i;
                }
            }
        }
        if (collision.isPresent()) {
            // Calculate bouncing vector and position of resulting position
            var dirUnitVec = segment.end().diff(collision.get()).normalize();
            var wallUnitVec = arenaSegments.get(bounceWall).start().diff(collision.get()).normalize();
            var dAngle = Math.acos(dirUnitVec.x());
            if (dirUnitVec.y() > 0) {
                dAngle = 2 * Math.PI - dAngle;
            }
            var pAngle = Math.acos(wallUnitVec.x());
            if (wallUnitVec.y() > 0) {
                pAngle = 2 * Math.PI - pAngle;
            }
            var dpAngle = 2 * pAngle - dAngle;
            while (dpAngle >= 2 * Math.PI) {
                dpAngle -= 2 * Math.PI;
            }
            var newPos = collision.get().add(
                    new Point(Math.cos(dpAngle), -Math.sin(dpAngle))
                            .mult(segment.size() - minDistance));
            var resultVector = new Segment(collision.get(), newPos);
            // Recursive call to check if the result vector itself is bouncing again
            var nextBounce = bounce(resultVector, bounceWall);
            if (nextBounce.didBounce()) {
                // Bounced again in recursive call => return it
                return nextBounce;
            }
            return StadiumBounce.collided(newPos, resultVector.derivate().normalize());
        }
        return StadiumBounce.noBounced();
    }

    private boolean isOutside(Point pos) {
        return pos.x() < left || pos.x() > left + width || pos.y() < top || pos.y() > top + height;
    }

    private void info(RoutingContext ctx) {
        ctx.request().bodyHandler(it -> {
            var input = it.toJsonObject();
            var isVisitors = input.getBoolean("isVisitors");
            var oppGoal = (isVisitors ? goalA : goalB).middle();
            var ownGoal = (isVisitors ? goalB : goalA).middle();
            var direction = oppGoal.diff(ownGoal).normalize();
            var zone = getDefendZone(ownGoal, direction);
            var arenaInfo = new ArenaInfo(zone.start(), zone.end(), oppGoal);
            ctx.response().end(JsonObject.mapFrom(arenaInfo).toString());
        });
    }

    private Segment getDefendZone(Point goalMiddle, Point txUnitVec) {
        var boxHalfSize = Math.min(width, height) / 4.0;
        var topLeft = new Point(-boxHalfSize, -boxHalfSize);
        var bottomRight = new Point(boxHalfSize, boxHalfSize);
        return new Segment(topLeft, bottomRight)
                .add(goalMiddle)
                .add(txUnitVec.mult(boxHalfSize));
    }

    private String getScoreText() {
        var elapsed = Math.min(matchTime, (System.currentTimeMillis() - startTime) / 1000);
        var minutes = elapsed / 60;
        var seconds = elapsed % 60;
        var text = locals + ": " + scoreA + " - " + visitors + ": " + scoreB + " ~~ Time: " + adjust(minutes) + ":" + adjust(seconds);
        if (elapsed == matchTime) {
            if (scoreA == scoreB) {
                return text + " ~~ Draw game!";
            } else {
                return text + " ~~ " + (scoreA > scoreB ? locals : visitors) + " win!";
            }
        }
        return text;
    }

    private String adjust(long number) {
        return number < 10 ? "0" + number : String.valueOf(number);
    }

    private void display() {
        displayer.send(stadiumGO);
        displayer.send(scoreGO.withText(name + " - " + getScoreText()));
    }

    public static void main(String[] args) {
        var vertx = Commons.vertx();
        var client = WebClient.create(vertx);
        var displayer = new Displayer(vertx, client);
        vertx.deployVerticle(new StadiumVerticle(client, displayer));
    }
}
