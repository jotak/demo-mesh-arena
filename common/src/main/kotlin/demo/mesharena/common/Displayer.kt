package demo.mesharena.common

import demo.mesharena.common.Commons.kafkaProducer
import io.opentracing.Tracer
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kafka.client.producer.KafkaProducerRecord
import io.vertx.tracing.opentracing.OpenTracingUtil

class Displayer(vertx: Vertx, private val client: WebClient, private val tracer: Tracer?) {
    private val kafkaProducer = kafkaProducer(vertx)

    companion object {
        const val TOPIC = "display"
    }

    fun send(obj: GameObject) {
        // Init Display trace
        val json = JsonObject.mapFrom(obj)
        val span = tracer?.buildSpan("Display")?.ignoreActiveSpan()?.start()
        OpenTracingUtil.setSpan(span)
        if (kafkaProducer != null) {
            kafkaProducer.write(KafkaProducerRecord.create(TOPIC, json))
                    .onFailure { it.printStackTrace() }
        } else {
            client.post(Commons.UI_PORT, Commons.UI_HOST, "/display").sendJson(json) {
                if (!it.succeeded()) {
                    it.cause().printStackTrace()
                }
            }
        }
        span?.finish()
    }
}
