package demo.mesharena.common

import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import io.vertx.core.MultiMap

/**
 * @author Pavol Loffay
 */
object TracingHeaders {
    fun inject(tracer: Tracer, spanContext: SpanContext, headers: MultiMap) {
        tracer.inject(spanContext, Format.Builtin.HTTP_HEADERS, object : TextMap {
            override fun iterator(): MutableIterator<Map.Entry<String, String>> {
                throw UnsupportedOperationException()
            }

            override fun put(key: String, value: String) {
                headers.add(key, value)
            }
        })
    }

    fun extract(tracer: Tracer, headers: MultiMap): SpanContext {
        return tracer.extract(Format.Builtin.HTTP_HEADERS, object : TextMap {
            override fun iterator(): MutableIterator<Map.Entry<String, String>> {
                return headers.iterator()
            }

            override fun put(key: String, value: String) {
                throw UnsupportedOperationException()
            }
        })
    }
}
