package demo.mesharena.common;

import io.opentracing.propagation.TextMap;
import io.vertx.core.MultiMap;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * @author Pavol Loffay
 */
public class TracingContext implements TextMap {

  private final MultiMap headers;

  public TracingContext(MultiMap headers) {
    this.headers = headers;
  }

  @Override
  public Iterator<Entry<String, String>> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void put(String key, String value) {
    headers.add(key, value);
  }
}
