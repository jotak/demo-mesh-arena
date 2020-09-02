package demo.mesharena.common;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;

import java.util.Optional;

public class DisplayMessager {
  private final WebClient client;
  private final Optional<KafkaProducer<String, JsonObject>> kafkaProducer;

  public DisplayMessager(Vertx vertx, WebClient client) {
    this.client = client;
    kafkaProducer = Commons.kafkaProducer(vertx);
  }

  public void send(JsonObject json) {
    if (kafkaProducer.isPresent()) {
      kafkaProducer.get().write(KafkaProducerRecord.create("display", json)).onFailure(Throwable::printStackTrace);
    } else {
      client.post(Commons.UI_PORT, Commons.UI_HOST, "/display").sendJson(json, ar -> {
        if (!ar.succeeded()) {
          ar.cause().printStackTrace();
        }
      });
    }
  }
}
