package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val zero = Point(0.0, 0.0)

data class Point @JsonCreator constructor(
        @JsonProperty("x") val x: Double,
        @JsonProperty("y") val y: Double
) : Jsonisable {
  fun add(other: Point): Point {
    return Point(x + other.x, y + other.y)
  }

  fun diff(other: Point): Point {
    return Point(x - other.x, y - other.y)
  }

  fun mult(scalar: Double): Point {
    return Point(x * scalar, y * scalar)
  }

  operator fun div(scalar: Double): Point {
    return Point(x / scalar, y / scalar)
  }

  fun size(): Double {
    return sqrt(x * x + y * y)
  }

  fun normalize(): Point {
    val size = size()
    return if (size != 0.0) {
      div(size)
    } else zero
  }

  fun rotate(angle: Double): Point {
    val cos: Double = cos(angle)
    val sin: Double = sin(angle)
    return Point(x * cos - y * sin, x * sin + y * cos)
  }

  override fun toString(): String {
    return "Point{x=${x}, y=${y}}"
  }
}
