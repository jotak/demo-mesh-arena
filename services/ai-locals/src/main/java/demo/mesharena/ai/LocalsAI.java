package demo.mesharena.ai;

import io.vertx.core.Vertx;

public class LocalsAI extends AI {
  public LocalsAI() {
    super(false);
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new LocalsAI());
  }
}
