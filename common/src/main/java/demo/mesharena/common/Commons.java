package demo.mesharena.common;

import io.jaegertracing.Configuration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.tracing.opentracing.OpenTracingOptions;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class Commons {

  public static final int METRICS_ENABLED = Commons.getIntEnv("METRICS_ENABLED", 0);
  public static final int TRACING_ENABLED = Commons.getIntEnv("TRACING_ENABLED", 0);
  public static final String KAFKA_ADDRESS = Commons.getStringEnv("KAFKA_ADDRESS", "");

  public static final int UI_PORT = getIntEnv("MESHARENA_UI_PORT", 8080);
  public static final String UI_HOST = getStringEnv("MESHARENA_UI_HOST", "localhost");
  public static final int BALL_PORT = getIntEnv("MESHARENA_BALL_PORT", 8081);
  public static final String BALL_HOST = getStringEnv("MESHARENA_BALL_HOST", "localhost");
  public static final int STADIUM_PORT = getIntEnv("MESHARENA_STADIUM_PORT", 8082);
  public static final String STADIUM_HOST = getStringEnv("MESHARENA_STADIUM_HOST", "localhost");

  private Commons() {
  }

  public static String getIP() {
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      return "unknown";
    }
  }

  public static Optional<Tracer> createTracerFromEnv() {
    if (TRACING_ENABLED == 1) {
      return Optional.of(Configuration.fromEnv().getTracer());
    }
    return Optional.empty();
  }

  public static Optional<Tracer> createHardcodedTracer(String service) {
    if (TRACING_ENABLED == 1) {
      Configuration c = new Configuration(service)
          .withSampler(new Configuration.SamplerConfiguration()
              .withType("const")
              .withParam(1))
          .withCodec(new Configuration.CodecConfiguration()
              .withPropagation(Configuration.Propagation.B3))
          .withReporter(new Configuration.ReporterConfiguration()
              .withSender(new Configuration.SenderConfiguration().withEndpoint("http://localhost:14268/api/traces")));
      return Optional.of(c.getTracer());
    }
    return Optional.empty();
  }

  public static Optional<KafkaConsumer<String, JsonObject>> kafkaConsumer(Vertx vertx, String groupId) {
    if (KAFKA_ADDRESS.equals("")) {
      return Optional.empty();
    }
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KAFKA_ADDRESS);
    config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    config.put("value.deserializer", "io.vertx.kafka.client.serialization.JsonObjectDeserializer");
    config.put("group.id", groupId);
    config.put("auto.offset.reset", "latest");
    config.put("enable.auto.commit", "false");
    return Optional.of(KafkaConsumer.create(vertx, config));
  }

  public static Optional<KafkaProducer<String, JsonObject>> kafkaProducer(Vertx vertx) {
    if (KAFKA_ADDRESS.equals("")) {
      return Optional.empty();
    }
    Map<String, String> config = new HashMap<>();
    config.put("bootstrap.servers", KAFKA_ADDRESS);
    config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    config.put("value.serializer", "io.vertx.kafka.client.serialization.JsonObjectSerializer");
    config.put("acks", "0");
    KafkaProducer<String, JsonObject> p = KafkaProducer.create(vertx, config);
    p.exceptionHandler(Throwable::printStackTrace);
    return Optional.of(p);
  }

  public static String getStringEnv(String varname, String def) {
    String val = System.getenv(varname);
    return (val == null || val.isEmpty()) ? def : val;
  }

  public static String getHTMLStringEnv(String varname, String def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      System.out.println(varname + " = " + html(val));
      return html(val);
    }
  }

  public static int getIntEnv(String varname, int def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      return Integer.parseInt(val);
    }
  }

  public static double getDoubleEnv(String varname, double def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      return Double.parseDouble(val);
    }
  }

  public static String html(String str) {
    StringBuilder out = new StringBuilder();
    for (char c : str.toCharArray()) {
      if (!Character.isLetterOrDigit(c)) {
        out.append(String.format("&#x%x;", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  public static Vertx vertx(Optional<Tracer> tracer) {
    VertxOptions opts = new VertxOptions();
    tracer.ifPresent(t -> opts.setTracingOptions(new OpenTracingOptions(t)));
    if (METRICS_ENABLED == 1) {
      opts.setMetricsOptions(new MicrometerMetricsOptions()
          .setPrometheusOptions(new VertxPrometheusOptions()
              .setPublishQuantiles(true)
              .setEnabled(true))
          .setLabels(EnumSet.of(Label.POOL_TYPE, Label.POOL_NAME, Label.CLASS_NAME, Label.HTTP_CODE, Label.HTTP_METHOD, Label.HTTP_PATH, Label.EB_ADDRESS, Label.EB_FAILURE, Label.EB_SIDE))
          .setEnabled(true));
    }
    Vertx vertx = Vertx.vertx(opts);
    if (METRICS_ENABLED == 1) {
      // Instrument JVM
      MeterRegistry registry = BackendRegistries.getDefaultNow();
      if (registry != null) {
        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        // new JvmGcMetrics().bindTo(registry);
        // new ProcessorMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
      }
    }
    return vertx;
  }
}
