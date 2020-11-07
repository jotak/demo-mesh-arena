package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty


class StadiumBounce {
  data class Rq @JsonCreator constructor(
          @JsonProperty("seg") val seg: Segment
  ) : Jsonisable

  data class Rs @JsonCreator constructor(
          @JsonProperty("scoredTeam") val scoredTeam: String?,
          @JsonProperty("collision") val collision: Collision?
  ) : Jsonisable {
    fun didBounce(): Boolean {
      return scoredTeam != null || collision != null
    }
  }

  data class Collision @JsonCreator constructor(
          @JsonProperty("pos") val pos: Point,
          @JsonProperty("vec") val vec: Point
  ) : Jsonisable

  companion object {
    fun noBounced(): Rs {
      return Rs(null, null)
    }

    fun collided(pos: Point, vec: Point): Rs {
      return Rs(null, Collision(pos, vec))
    }

    fun scored(team: String): Rs {
      return Rs(team, null)
    }
  }
}
