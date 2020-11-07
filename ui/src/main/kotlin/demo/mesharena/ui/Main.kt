package demo.mesharena.ui

import demo.mesharena.common.Commons


/**
 * @author Joel Takvorian
 */
fun main() {
  val tracer = Commons.createTracerFromEnv()
  Commons.vertx(tracer).deployVerticle(UI(tracer))
}
