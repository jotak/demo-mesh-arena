package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.security.SecureRandom


class BallControl {
  data class Rq @JsonCreator constructor(
    @JsonProperty("pos") val pos: Point,
    @JsonProperty("player") val player: Player
  ) : Jsonisable

  data class Rs @JsonCreator constructor(
    @JsonProperty("pos") val pos: Point,
    @JsonProperty("success") val success: Boolean,
    @JsonProperty("takesBall") val takesBall: Boolean
  ) : Jsonisable

  data class Player @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("skill") var skill: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("team") val team: String
  ) : Jsonisable {
    fun takesBall(controllingPlayer: Player?, rnd: SecureRandom): Boolean {
      return controllingPlayer == null || rnd.nextInt(2 * controllingPlayer.skill + skill) < skill
    }
  }

  companion object {
    fun ballKept(pos: Point): Rs {
      return Rs(pos, success = true, takesBall = false)
    }

    fun ballTaken(pos: Point): Rs {
      return Rs(pos, success = true, takesBall = true)
    }

    fun ballMissed(pos: Point): Rs {
      return Rs(pos, success = false, takesBall = false)
    }
  }
}
