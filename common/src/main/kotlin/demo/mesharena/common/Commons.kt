package demo.mesharena.common

import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.opentracing.Tracer
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.kafka.client.consumer.KafkaConsumer
import io.vertx.kafka.client.producer.KafkaProducer
import io.vertx.micrometer.Label
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import io.vertx.micrometer.backends.BackendRegistries
import io.vertx.tracing.opentracing.OpenTracingOptions
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

object Commons {

  val METRICS_ENABLED = getIntEnv("METRICS_ENABLED", 0)
  val TRACING_ENABLED = getIntEnv("TRACING_ENABLED", 0)
  val KAFKA_ADDRESS = getStringEnv("KAFKA_ADDRESS", "")

  val UI_PORT = getIntEnv("MESHARENA_UI_PORT", 8080)
  val UI_HOST = getStringEnv("MESHARENA_UI_HOST", "localhost")
  val BALL_PORT = getIntEnv("MESHARENA_BALL_PORT", 8081)
  val BALL_HOST = getStringEnv("MESHARENA_BALL_HOST", "localhost")
  val STADIUM_PORT = getIntEnv("MESHARENA_STADIUM_PORT", 8082)
  val STADIUM_HOST = getStringEnv("MESHARENA_STADIUM_HOST", "localhost")

  fun getIP(): String {
    return try {
      InetAddress.getLocalHost().hostAddress
    } catch (e: UnknownHostException) {
      "unknown"
    }
  }

  fun createTracerFromEnv(): Tracer? {
    return if (TRACING_ENABLED == 1) {
      fromEnv().tracer
    } else null
  }

  fun createHardcodedTracer(service: String): Tracer? {
    if (TRACING_ENABLED == 1) {
      val c = Configuration(service)
              .withSampler(SamplerConfiguration()
                      .withType("const")
                      .withParam(1))
              .withCodec(CodecConfiguration()
                      .withPropagation(Propagation.B3))
              .withReporter(ReporterConfiguration()
                      .withSender(SenderConfiguration().withEndpoint("http://localhost:14268/api/traces")))
      return c.tracer
    }
    return null
  }

  fun kafkaConsumer(vertx: Vertx, groupId: String): KafkaConsumer<String, JsonObject>? {
    if (KAFKA_ADDRESS == "") {
      return null
    }
    val config = mapOf(
            Pair("bootstrap.servers", KAFKA_ADDRESS),
            Pair("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer"),
            Pair("value.deserializer", "io.vertx.kafka.client.serialization.JsonObjectDeserializer"),
            Pair("group.id", groupId),
            Pair("auto.offset.reset", "latest"),
            Pair("enable.auto.commit", "false")
    )
    return KafkaConsumer.create<String, JsonObject>(vertx, config)
  }

  fun kafkaProducer(vertx: Vertx): KafkaProducer<String, JsonObject>? {
    if (KAFKA_ADDRESS == "") {
      return null
    }
    val config = mapOf(
            Pair("bootstrap.servers", KAFKA_ADDRESS),
            Pair("key.serializer", "org.apache.kafka.common.serialization.StringSerializer"),
            Pair("value.serializer", "io.vertx.kafka.client.serialization.JsonObjectSerializer"),
            Pair("acks", "0")
    )
    val p = KafkaProducer.create<String, JsonObject>(vertx, config)
    p.exceptionHandler { it.printStackTrace() }
    return p
  }

  fun getStringEnv(varname: String, def: String): String {
    val v = System.getenv(varname)
    return if (v == null || v.isEmpty()) def else v
  }

  fun getIntEnv(varname: String, def: Int): Int {
    val v = System.getenv(varname)
    return if (v == null || v.isEmpty()) {
      def
    } else {
      v.toInt()
    }
  }

  fun getBoolEnv(varname: String, def: Boolean): Boolean {
    val v = System.getenv(varname)
    return if (v == null || v.isEmpty()) {
      def
    } else {
      v.toBoolean()
    }
  }

  fun getDoubleEnv(varname: String, def: Double): Double {
    val v = System.getenv(varname)
    return if (v == null || v.isEmpty()) {
      def
    } else {
      v.toDouble()
    }
  }

  fun vertx(tracer: Tracer?): Vertx {
    val opts = VertxOptions()
    if (tracer != null) {
      opts.tracingOptions = OpenTracingOptions(tracer)
    }
    if (METRICS_ENABLED == 1) {
      opts.metricsOptions = MicrometerMetricsOptions()
              .setPrometheusOptions(VertxPrometheusOptions()
                      .setPublishQuantiles(true)
                      .setEnabled(true))
              .setLabels(EnumSet.of(Label.POOL_TYPE, Label.POOL_NAME, Label.CLASS_NAME, Label.HTTP_CODE, Label.HTTP_METHOD, Label.HTTP_PATH, Label.EB_ADDRESS, Label.EB_FAILURE, Label.EB_SIDE))
              .setEnabled(true)
    }
    val vertx = Vertx.vertx(opts)
    if (METRICS_ENABLED == 1) {
      // Instrument JVM
      val registry = BackendRegistries.getDefaultNow()
      if (registry != null) {
        ClassLoaderMetrics().bindTo(registry)
        JvmMemoryMetrics().bindTo(registry)
        // JvmGcMetrics().bindTo(registry);
        // ProcessorMetrics().bindTo(registry);
        JvmThreadMetrics().bindTo(registry)
      }
    }
    return vertx.exceptionHandler { it.printStackTrace() }
  }
}
