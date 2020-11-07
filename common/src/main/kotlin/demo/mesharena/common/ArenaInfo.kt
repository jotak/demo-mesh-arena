package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.security.SecureRandom

data class ArenaInfo @JsonCreator constructor(
        @JsonProperty("defendZoneTopLeft") val defendZoneTopLeft: Point,
        @JsonProperty("defendZoneBottomRight") val defendZoneBottomRight: Point,
        @JsonProperty("goal") val goal: Point
) : Jsonisable {
    fun randomDefendPoint(rnd: SecureRandom): Point {
        val (x, y) = defendZoneBottomRight.diff(defendZoneTopLeft)
        return Point(rnd.nextInt(x.toInt()).toDouble(), rnd.nextInt(y.toInt()).toDouble())
                .add(defendZoneTopLeft)
    }
}