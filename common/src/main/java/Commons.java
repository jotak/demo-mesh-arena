import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.backends.BackendRegistries;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;

public class Commons {
    public static final boolean METRICS_ENABLED = getBoolEnv("METRICS_ENABLED", false);
    public static final boolean TRACING_ENABLED = getBoolEnv("TRACING_ENABLED", false);
    public static final String KAFKA_ADDRESS = getStringEnv("KAFKA_ADDRESS", "");

    public static final int UI_PORT = getIntEnv("MESHARENA_UI_PORT", 8080);;
    public static final String UI_HOST = getStringEnv("MESHARENA_UI_HOST", "localhost");
    public static final int BALL_PORT = getIntEnv("MESHARENA_BALL_PORT", 8081);
    public static final String BALL_HOST = getStringEnv("MESHARENA_BALL_HOST", "localhost");
    public static final int STADIUM_PORT = getIntEnv("MESHARENA_STADIUM_PORT", 8082);
    public static final String STADIUM_HOST = getStringEnv("MESHARENA_STADIUM_HOST", "localhost");
    public static final int PLAYER_PORT = getIntEnv("MESHARENA_PLAYER_PORT", 8090);

    public static String getStringEnv(String varname, String def) {
        var v = System.getenv(varname);
        return v == null ? def : v;
    }

    public static int getIntEnv(String varname, int def) {
        var v = System.getenv(varname);
        if (v == null || v.isEmpty()) {
            return def;
        } else {
            return Integer.parseInt(v);
        }
    }

    public static double getDoubleEnv(String varname, double def) {
        var v = System.getenv(varname);
        if (v == null || v.isEmpty()) {
            return def;
        } else {
            return Double.parseDouble(v);
        }
    }

    public static boolean getBoolEnv(String varname, boolean def) {
        var v = System.getenv(varname);
        if (v == null || v.isEmpty()) {
            return def;
        } else {
            return Boolean.parseBoolean(v);
        }
    }

    public static String getIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    public static Vertx vertx() {
        var opts = new VertxOptions();
        if (METRICS_ENABLED) {
            opts.setMetricsOptions(new MicrometerMetricsOptions()
                    .setPrometheusOptions(new VertxPrometheusOptions()
                            .setPublishQuantiles(true)
                            .setEnabled(true))
                    .setLabels(EnumSet.of(Label.POOL_TYPE, Label.POOL_NAME, Label.CLASS_NAME, Label.HTTP_CODE, Label.HTTP_METHOD, Label.HTTP_PATH, Label.EB_ADDRESS, Label.EB_FAILURE, Label.EB_SIDE))
                    .setEnabled(true)
            );
        }
        var vertx = Vertx.vertx(opts);
        if (METRICS_ENABLED) {
            // Instrument JVM
            var registry = BackendRegistries.getDefaultNow();
            if (registry != null) {
                new ClassLoaderMetrics().bindTo(registry);
                new JvmMemoryMetrics().bindTo(registry);
                // JvmGcMetrics().bindTo(registry);
                // ProcessorMetrics().bindTo(registry);
                new JvmThreadMetrics().bindTo(registry);
            }
        }
        return vertx.exceptionHandler(Throwable::printStackTrace);
    }
}
