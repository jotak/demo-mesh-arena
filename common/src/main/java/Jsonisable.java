import io.vertx.core.json.JsonObject;

public interface Jsonisable {
    default JsonObject json() {
        return JsonObject.mapFrom(this);
    }

    default String jsonString() {
        return json().toString();
    }
}
