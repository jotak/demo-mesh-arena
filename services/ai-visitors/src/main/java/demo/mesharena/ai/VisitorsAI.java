package demo.mesharena.ai;

import io.vertx.core.Vertx;

public class VisitorsAI extends AI {
  public VisitorsAI() {
    super(true);
  }

  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new VisitorsAI());
  }
}
