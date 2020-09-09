package demo.mesharena.common;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public class KafkaTracer {
  public static void send(KafkaProducer<String, JsonObject> producer, Optional<Tracer> tracer, String eventName, JsonObject json) {
    Optional<Span> span = tracer.map(t -> {
      Span s = t.buildSpan("send_" + eventName + "_event")
          .withTag("hostname", Commons.HOSTNAME)
          .withTag("to_addr", Commons.KAFKA_ADDRESS)
          .start();
      injectTrace(t, s.context(), json);
      return s;
    });
    producer.write(KafkaProducerRecord.create(eventName, json))
        .onFailure(t -> {
          span.ifPresent(s -> s.setTag("error", true).setTag("errMsg", t.getMessage()).finish());
          t.printStackTrace();
        })
        .onSuccess(v -> span.ifPresent(Span::finish));
  }

  public static void receive(KafkaConsumer<String, JsonObject> consumer, Optional<Tracer> tracer, String eventName, BiConsumer<JsonObject, Optional<Span>> handler) {
    consumer.subscribe(DisplayMessager.EVENT_NAME)
        .onSuccess(v -> consumer.handler(rec -> {
          JsonObject json = rec.value();
          Optional<Span> span = tracer.flatMap(t ->
              extractTrace(t, json).map(ctx ->
                  t.buildSpan("received_" + eventName + "_event")
                      .withTag("hostname", Commons.HOSTNAME)
                      .withTag("from_addr", Commons.KAFKA_ADDRESS)
                      .asChildOf(ctx)
                      .start()));
          handler.accept(json, span);
        }))
        .onFailure(Throwable::printStackTrace);
  }

  private static void injectTrace(Tracer tracer, SpanContext spanContext, JsonObject obj) {
    JsonObject traceObj = new JsonObject();
    tracer.inject(spanContext, Format.Builtin.HTTP_HEADERS, new TextMap() {
      @NotNull
      @Override
      public Iterator<Map.Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void put(String key, String value) {
        traceObj.put(key, value);
      }
    });
    obj.put("_trace", traceObj);
  }

  private static Optional<SpanContext> extractTrace(Tracer tracer, JsonObject obj) {
    if (obj.containsKey("_trace")) {
      JsonObject traceObj = obj.getJsonObject("_trace");
      SpanContext ctx = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMap() {
        @NotNull
        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
          return (Iterator<Map.Entry<String, String>>) (Object) traceObj.iterator();
        }

        @Override
        public void put(String key, String value) {
          throw new UnsupportedOperationException();
        }
      });
      return Optional.of(ctx);
    }
    return Optional.empty();
  }
}
