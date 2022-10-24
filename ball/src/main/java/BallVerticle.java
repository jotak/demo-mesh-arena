import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.backends.BackendRegistries;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.Consumer;

public class BallVerticle extends AbstractVerticle {
    private final int deltaMs = Commons.getIntEnv("DELTA_MS", 200);
    private final double resistance = Commons.getDoubleEnv("RESISTANCE", 80);
    private final double pctErrors = Commons.getDoubleEnv("PCT_ERRORS", 0);
    private final String image = Commons.getStringEnv("IMAGE", "ball");

    private final SecureRandom rnd = new SecureRandom();
    private final Style style = new Style()
            .dimensions(16, 16)
            .image("./" + image + ".png")
            .zIndex(5)
            .transition(deltaMs);
    private final GameObject ballGO = new GameObject(
            "ball-" + UUID.randomUUID(),
            style.toString(),
            0, 0, null, null);
    private final MeterRegistry registry = BackendRegistries.getDefaultNow();

    private Point speed = Point.ZERO;
    private BallControl.Player controllingPlayer = null;
    private double controllingPlayerSkillTimer = 0.0;
    private Point pos = new Point(50.0, 50.0);
    private double interactTimer = 0.0;
    private double errorTimer = 0.0;

    private final WebClient client;
    private final Displayer displayer;

    public BallVerticle(WebClient client, Displayer displayer) {
        this.client = client;
        this.displayer = displayer;
    }

    @Override
    public void start() throws Exception {
        System.out.println("start");
        if (registry != null) {
            Gauge.builder("mesharena_ball_speed", () -> speed.size())
                    .description("Ball speed gauge")
                    .register(registry);
        }

        var router = Router.router(vertx);

        if (Commons.METRICS_ENABLED) {
            router.route("/metrics").handler(PrometheusScrapingHandler.create());
        }

        router.route("/health").handler(it -> it.response().end());
        router.route("/hasControl").handler(this::hasControl);
        router.put("/shoot").handler(this::shoot);
        router.put("/setPosition").handler(this::setPosition);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(Commons.BALL_PORT)
                .onSuccess(server ->
                        System.out.println("HTTP server started on port " + server.actualPort())
                );

        // Ping-display
        vertx.setPeriodic(2000, a -> display());

        // Start game loop
        vertx.setPeriodic(deltaMs, a -> update(deltaMs / 1000.0));
    }

    private void hasControl(RoutingContext ctx) {
        ctx.request().bodyHandler(it -> {
            if (rnd.nextInt(100) < pctErrors) {
                errorTimer = 0.0;
                ctx.response().setStatusCode(503).setStatusMessage("faiiiiilure! (to test outlier detection)").end();
                return;
            }
            interactTimer = 0.0;
            var rq = it.toJsonObject().mapTo(BallControl.Rq.class);
            var distanceToBall = pos.diff(rq.pos()).size();
            final BallControl.Rs rs;
            if (distanceToBall < 15) {
                if (controllingPlayer != null && rq.player().id().equals(controllingPlayer.id())) {
                    controllingPlayer = controllingPlayer.withSkill(rq.player().skill());
                    rs = BallControl.ballKept(pos);
                } else if (rq.player().takesBall(controllingPlayer, rnd)) {
                    controllingPlayer = rq.player();
                    if (registry != null) {
                        Counter.builder("mesharena_take_ball")
                                .description("Counter of player taking control of the ball")
                                .tag("team", rq.player().team())
                                .tag("player", rq.player().name())
                                .register(registry)
                                .increment();
                    }
                    rs = BallControl.ballTaken(pos);
                } else {
                    rs = BallControl.ballMissed(pos);
                }
            } else {
                rs = BallControl.ballMissed(pos);
            }
            ctx.response().end(rs.jsonString());
        });
    }

    private void shoot(RoutingContext ctx) {
        ctx.request().bodyHandler(it -> {
            var rq = it.toJsonObject().mapTo(BallShoot.Rq.class);
            speed = rq.vec();
            var playerTeam = controllingPlayer != null ? controllingPlayer.team() : "?";
            var playerName = controllingPlayer != null ? controllingPlayer.name() : "?";
            if ("togoal".equals(rq.kind())) {
                if (rnd.nextInt(2) == 0) {
                    comment(playerName + " shooting!");
                } else {
                    comment("Wooow " + playerName + " tries his luck!");
                }
                if (registry != null) {
                    Counter.builder("mesharena_shoots")
                            .description("Shoots counter")
                            .tag("team", controllingPlayer != null ? controllingPlayer.team() : "")
                            .tag("player", controllingPlayer != null ? controllingPlayer.name() : "")
                            .register(registry)
                            .increment();
                }
            } else if ("forward".equals(rq.kind())) {
                if (rnd.nextInt(2) == 0) {
                    comment("Still " + playerName + "...");
                } else {
                    comment(playerName + " again...");
                }
            } else if ("defensive".equals(rq.kind())) {
                if (rnd.nextInt(2) == 0) {
                    comment("Defensive shooting from " + playerName);
                } else {
                    comment(playerName + " takes the ball and shoots!");
                }
            } else if ("control".equals(rq.kind())) {
                comment(playerName + " takes back the ball");
            }
            ctx.response().end();
        });
    }

    private void comment(String text) {
        displayer.send(new GameObject(
                "ball-comment",
                new Style()
                        .position((int)pos.y() + 10, (int)pos.x() + 10)
                        .zIndex(10)
                        .other("color: brown; font-weight: bold;")
                        .toString(),
                0, 0, text, null
        ));
    }

    private void setPosition(RoutingContext ctx) {
        System.out.println("setPosition");
        ctx.request().bodyHandler(it -> {
            var json = it.toJsonObject();
            var x = json.getDouble("x");
            var y = json.getDouble("y");
            pos = new Point(x, y);
            System.out.println("setPosition: " + pos);
            speed = Point.ZERO;
            controllingPlayer = null;
            ctx.response().end();
            display();
        });
    }

    private void update(double delta) {
        var oldSpeed = speed.size();
        if (oldSpeed > 0) {
            var oldPos = pos;
            var newPos = pos.add(speed.mult(delta));
            var newSpeed = Math.max(0.0, oldSpeed - resistance * delta);
            checkBounce(new Segment(pos, newPos), newSpeed, didBounce -> {
                if (!didBounce) {
                    pos = newPos;
                    speed = speed.mult(newSpeed / oldSpeed);
                }
                if (oldPos != pos) {
                    display();
                }
            });
        }
        // Decrease controlling skill
        if (controllingPlayer != null && controllingPlayer.skill() > 0) {
            controllingPlayerSkillTimer += delta;
            if (controllingPlayerSkillTimer >= 0.5) {
                controllingPlayer = controllingPlayer.withSkill(controllingPlayer.skill() - 1);
                controllingPlayerSkillTimer = 0.0;
            }
        }
        interactTimer += delta;
        errorTimer += delta;
    }

    private void checkBounce(Segment seg, double newSpeed, Consumer<Boolean> handler) {
        var request = client.post(Commons.STADIUM_PORT, Commons.STADIUM_HOST, "/bounce");
        var curPlayer = controllingPlayer != null ? controllingPlayer : new BallControl.Player("?", 0, "?", "?");
        request.timeout(1000).sendJson(new StadiumBounce.Rq(seg), it -> {
            if (!it.succeeded()) {
                // No stadium => no bounce
                handler.accept(false);
            } else {
                var r = it.result();
                try {
                    var bounce = r.bodyAsJsonObject().mapTo(StadiumBounce.Rs.class);
                    var collision = bounce.collision();
                    if (bounce.scoredTeam() != null) {
                        var isOwn = !bounce.scoredTeam().equals(curPlayer.team());
                        if (isOwn) {
                            comment("Ohhhh own goal from " + curPlayer.name() + " !!");
                        } else {
                            comment("Goaaaaaaal by " + curPlayer.name() + " !!!!");
                        }
                        if (registry != null) {
                            Counter.builder("mesharena_goals")
                                    .description("Goals counter")
                                    .tag("team", curPlayer.team())
                                    .tag("player", curPlayer.name())
                                    .tag("own_goal", isOwn ? "yes" : "no")
                                    .register(registry)
                                    .increment();
                        }
                        // Do not update position, Stadium will do it
                        controllingPlayer = null;
                        speed = Point.ZERO;
                        handler.accept(true);
                    } else if (collision != null) {
                        pos = collision.pos();
                        speed = collision.vec().mult(newSpeed);
                        handler.accept(true);
                    } else {
                        handler.accept(false);
                    }
                } catch (Exception e) {
                    handler.accept(false);
                    System.out.println(r.statusCode() + "/" + r.statusMessage());
                }
            }
        });
    }

    private void display() {
        displayer.send(ballGO
                .withX(pos.x() - 8)
                .withY(pos.y() - 8)
                .withStyle(getStyle()));
    }

    private String getStyle() {
        if (errorTimer < 3.0) {
            // filter: brightness(40%) sepia(100%) hue-rotate(-50deg) saturate(600%);
            // error ]0 (no err), 1 (err)]
            var error = 1.0 - errorTimer / 3.0;
            // hue rotate ]0 (no err), -50 (err)]
            int hue = (int) (-50 * error);
            // brightness [40 (err), 100 (no err)[
            int brightness = 40 + (int)(60.0 * (1.0 - error));
            // sepia [0 (no err), 100 (err)[
            int sepia = (int)(100.0 * error);
            // saturate ]100 (no err), 600 (err)]
            int saturate = 100 + (int)(500.0 * error);
            return style.brightHueSat(brightness, hue, saturate, sepia).toString();
        } else {
            // interact [0 (old), 1 (now)]
            var interact = 1.0 - Math.min(interactTimer, 3.0) / 3.0;
            // brightness [40 (old), 100 (now)]
            int brightness = 40 + (int)(60.0 * interact);
            return style.brightness(brightness).toString();
        }
    }

    public static void main(String[] args) {
        var vertx = Commons.vertx();
        var client = WebClient.create(vertx);
        var displayer = new Displayer(vertx, client);
        vertx.deployVerticle(new BallVerticle(client, displayer));
    }
}
