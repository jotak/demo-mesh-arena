package demo.mesharena.ai

import demo.mesharena.common.Commons
import demo.mesharena.common.Displayer
import io.vertx.ext.web.client.WebClient

/**
 * @author Joel Takvorian
 */
fun main() {
  val tracer = Commons.createTracerFromEnv()
  val vertx = Commons.vertx(tracer)
  val client = WebClient.create(vertx)
  val displayer = Displayer(vertx, client, tracer)
  vertx.deployVerticle(AI(client, tracer, displayer))
}
