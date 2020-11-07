package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class GameObject @JsonCreator constructor(
        @JsonProperty("id") val id: String,
        @JsonProperty("style") var style: String = "",
        @JsonProperty("x") var x: Double = 0.0,
        @JsonProperty("y") var y: Double = 0.0,
        @JsonProperty("text") var text: String? = null,
        @JsonProperty("playerRef") val playerRef: PlayerRef? = null
) : Jsonisable

data class PlayerRef @JsonCreator constructor(
        @JsonProperty("name") val name: String,
        @JsonProperty("ip") val ip: String
) : Jsonisable
