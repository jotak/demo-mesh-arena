package demo.mesharena.common;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.tracing.opentracing.OpenTracingUtil;

import java.util.Optional;

public class DisplayMessager {
  private static final String TOPIC = "display";

  private final WebClient client;
  private final Optional<KafkaProducer<String, JsonObject>> kafkaProducer;
  private final Optional<Tracer> tracer;

  public DisplayMessager(Vertx vertx, WebClient client, Optional<Tracer> tracer) {
    this.client = client;
    this.tracer = tracer;
    kafkaProducer = Commons.kafkaProducer(vertx);
  }

  public void send(JsonObject json) {
    // Init Display trace
    Optional<Span> span = tracer.map(t -> {
      Span s = t.buildSpan("Display").ignoreActiveSpan().start();
      OpenTracingUtil.setSpan(s);
      return s;
    });
    if (kafkaProducer.isPresent()) {
      kafkaProducer.get().write(KafkaProducerRecord.create(TOPIC, json))
          .onFailure(Throwable::printStackTrace);
    } else {
      client.post(Commons.UI_PORT, Commons.UI_HOST, "/display").sendJson(json, ar -> {
        if (!ar.succeeded()) {
          ar.cause().printStackTrace();
        }
      });
    }
    span.ifPresent(Span::finish);
  }
}
