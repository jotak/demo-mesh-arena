package demo.mesharena.ball;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

public class Ball extends AbstractVerticle {

  private Ball() {
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new Ball());
  }

  @Override
  public void start() throws Exception {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080));
    HttpClientRequest request = client.request(HttpMethod.POST, "/creategameobject", response -> {
      System.out.println("Received response with status code " + response.statusCode());
    });

    String json = new JsonObject()
        .put("id", "ball")
        .put("style", "position: absolute; top: 100px; left: 10px; background-color: red;")
        .put("text", "ball")
        .toString();
    request.putHeader("content-type", "application/json");
    request.putHeader("content-length", String.valueOf(json.length()));
    request.write(json);
    request.end();
  }
}
