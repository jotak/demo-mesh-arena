import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

record Displayer(Vertx vertx, WebClient client) {
    public void send(GameObject obj) {
        // Init Display trace
        var json = JsonObject.mapFrom(obj);
//        val span = tracer?.buildSpan("Display")?.ignoreActiveSpan()?.start()
//        OpenTracingUtil.setSpan(span)
//        if (kafkaProducer != null) {
//            kafkaProducer.write(KafkaProducerRecord.create(TOPIC, json))
//                    .onFailure { it.printStackTrace() }
//        } else {
        client.post(Commons.UI_PORT, Commons.UI_HOST, "/display").timeout(1000).sendJson(json, it -> {
            if (!it.succeeded()) {
                it.cause().printStackTrace();
            }
        });
    }
//        span?.finish()
//    }
}
