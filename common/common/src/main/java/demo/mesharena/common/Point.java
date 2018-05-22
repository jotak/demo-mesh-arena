/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package demo.mesharena.common;

import java.util.Objects;

/**
 * @author Joel Takvorian
 */
public final class Point {

  public static final Point ZERO = new Point(0, 0);

  final double x;
  final double y;

  public Point(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public Point add(Point other) {
    return new Point(x + other.x, y + other.y);
  }

  public Point diff(Point other) {
    return new Point(x - other.x, y - other.y);
  }

  public Point mult(double scalar) {
    return new Point(x * scalar, y * scalar);
  }

  public Point div(double scalar) {
    return new Point(x / scalar, y / scalar);
  }

  public double size() {
    return Math.sqrt(x * x + y * y);
  }

  public Point normalize() {
    double size = size();
    if (size != 0) {
      return div(size);
    }
    return ZERO;
  }

  public Point rotate(double angle) {
    double cos = Math.cos(angle);
    double sin = Math.sin(angle);
    return new Point(x * cos - y * sin, x * sin + y * cos);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Point point = (Point) o;
    return Double.compare(point.x, x) == 0 &&
        Double.compare(point.y, y) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(x, y);
  }

  @Override
  public String toString() {
    return "Point{" +
        "x=" + x +
        ", y=" + y +
        '}';
  }
}
