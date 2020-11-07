package demo.mesharena.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Segment @JsonCreator constructor(
        @JsonProperty("start") val start: Point,
        @JsonProperty("end") val end: Point
) : Jsonisable {

  fun derivate(): Point {
    return end.diff(start)
  }

  fun size(): Double {
    return derivate().size()
  }

  fun middle(): Point {
    return Point((start.x + end.x) / 2, (start.y + end.y) / 2)
  }

  fun add(p: Point): Segment {
    return Segment(start.add(p), end.add(p))
  }

  fun getCrossingPoint(other: Segment): Point? {
    if (size() == 0.0 || other.size() == 0.0) {
      return null
    }
    val (x, y) = start.diff(other.start)
    val vA = derivate()
    val (x1, y1) = other.derivate()
    val fDenom = vA.x * y1 - vA.y * x1
    if (fDenom == 0.0) { // Parallel
      return null
    }
    val t = (y * x1 - x * y1) / fDenom
    val s = (y * vA.x - x * vA.y) / fDenom
    return if (t < 0.0f || t > 1.0f || s < 0.0f || s > 1.0f) {
      // Lines crossing outside of segment
      null
    } else {
      start.add(vA.mult(t))
    }
  }

  override fun toString(): String {
    return "Segment{start=${start}, end=${end}}"
  }
}
