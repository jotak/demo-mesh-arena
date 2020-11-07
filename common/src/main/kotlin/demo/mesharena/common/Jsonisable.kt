package demo.mesharena.common

import io.vertx.core.json.JsonObject

interface Jsonisable {
    fun json(): JsonObject {
        return JsonObject.mapFrom(this)
    }
    fun jsonString(): String {
        return json().toString()
    }
}
