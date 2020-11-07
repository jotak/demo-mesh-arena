package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Rectangle @JsonCreator constructor(
        @JsonProperty("top") val top: Double,
        @JsonProperty("left") val left: Double,
        @JsonProperty("bottom") val bottom: Double,
        @JsonProperty("right") val right: Double
) : Jsonisable {
  fun middle(): Point {
    return Segment(Point(left, top), Point(right, bottom)).middle()
  }

  override fun toString(): String {
    return "Rectangle{top=${top}, left=${left}, bottom=${bottom}, right=${right}}"
  }
}
