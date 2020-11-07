package demo.mesharena.ball

import demo.mesharena.common.Commons
import demo.mesharena.common.Displayer
import io.opentracing.Tracer
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.CoroutineVerticle

/**
 * @author Joel Takvorian
 */
fun main() {
  val tracer = Commons.createTracerFromEnv()
  val vertx = Commons.vertx(tracer)
  val client = WebClient.create(vertx)
  val displayer = Displayer(vertx, client, tracer)
  vertx.deployVerticle(Ball(client, tracer, displayer))
}
