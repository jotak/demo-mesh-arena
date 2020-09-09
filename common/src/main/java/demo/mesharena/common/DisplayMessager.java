package demo.mesharena.common;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.kafka.client.producer.KafkaProducer;

import java.util.Optional;

public class DisplayMessager {
  public static final String EVENT_NAME = "display";

  private final WebClient client;
  private final Optional<KafkaProducer<String, JsonObject>> kafkaProducer;
  private final Optional<Tracer> tracer;

  public DisplayMessager(Vertx vertx, WebClient client, Optional<Tracer> tracer) {
    this.client = client;
    this.tracer = tracer;
    kafkaProducer = Commons.kafkaProducer(vertx);
  }

  public void send(JsonObject json) {
    if (kafkaProducer.isPresent()) {
      KafkaTracer.send(kafkaProducer.get(), tracer, EVENT_NAME, json);
    } else {
      client.post(Commons.UI_PORT, Commons.UI_HOST, "/display").sendJson(json, ar -> {
        if (!ar.succeeded()) {
          ar.cause().printStackTrace();
        }
      });
    }
  }
}
