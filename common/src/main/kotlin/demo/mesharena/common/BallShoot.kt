package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class BallShoot {
  data class Rq @JsonCreator constructor(
          @JsonProperty("vec") val vec: Point,
          @JsonProperty("kind") val kind: String,
          @JsonProperty("playerID") val playerID: String
  ) : Jsonisable
}
