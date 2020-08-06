package demo.mesharena.common;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.vertx.core.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map.Entry;

/**
 * @author Pavol Loffay
 */
public class TracingHeaders {
  public static void inject(Tracer tracer, SpanContext spanContext, MultiMap headers) {
    tracer.inject(spanContext, Format.Builtin.HTTP_HEADERS, new TextMap() {
      @NotNull
      @Override
      public Iterator<Entry<String, String>> iterator() {
        throw new UnsupportedOperationException();
      }
      @Override
      public void put(String key, String value) {
        headers.add(key, value);
      }
    });
  }

  public static SpanContext extract(Tracer tracer, MultiMap headers) {
    return tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMap() {
      @NotNull
      @Override
      public Iterator<Entry<String, String>> iterator() {
        return headers.iterator();
      }
      @Override
      public void put(String key, String value) {
        throw new UnsupportedOperationException();
      }
    });
  }
}
