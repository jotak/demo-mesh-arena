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

/**
 * @author Joel Takvorian
 */
public final class Segment {
  private Point start;
  private Point end;

  public Segment(Point start, Point end) {
    this.start = start;
    this.end = end;
  }

  public Point start() {
    return start;
  }

  public Point end() {
    return end;
  }

  public Point derivate() {
    return end.diff(start);
  }

  public double size() {
    return derivate().size();
  }

  public Point middle() {
    return new Point((start.x + end.x) / 2, (start.y + end.y) / 2);
  }

  public Segment add(Point p) {
    return new Segment(start.add(p), end.add(p));
  }

  public boolean isValid() {
    return start.x != end.x || start.y != end.y;
  }

  public Point getCrossingPoint(Segment other) {
    if (!isValid() || !other.isValid()) {
      return null;
    }

    Point v1 = start.diff(other.start);
    Point vA = derivate();
    Point vB = other.derivate();

    double fDenom = vA.x * vB.y - vA.y * vB.x;

    if (fDenom == 0.0f) {
      // Parallel
      return null;
    }

    double t = (v1.y * vB.x - v1.x * vB.y) / fDenom;
    double s = (v1.y * vA.x - v1.x * vA.y) / fDenom;

    if (t < 0.0f || t > 1.0f || s < 0.0f || s > 1.0f) {
      // Lines crossing outside of segment
      return null;
    } else {
      return start.add(vA.mult(t));
    }
  }

  @Override
  public String toString() {
    return "Segment{" +
        "start=" + start +
        ", end=" + end +
        '}';
  }
}
