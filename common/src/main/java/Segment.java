import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;


record Segment(
        @JsonProperty("start") Point start,
        @JsonProperty("end") Point end
) implements Jsonisable {
    public Point derivate() {
        return end.diff(start);
    }

    public double size() {
        return end.dist(start);
    }

    public Point middle() {
        return start.add(end).div(2);
    }

    public Segment add(Point p) {
        return new Segment(start.add(p), end.add(p));
    }

    public Optional<Point> getCrossingPoint(Segment other) {
        if (size() == 0.0 || other.size() == 0.0) {
            return Optional.empty();
        }
        var diff = start.diff(other.start);
        var vA = derivate();
        var vB = other.derivate();
        var fDenom = vA.x() * vB.y() - vA.y() * vB.x();
        if (fDenom == 0.0) { // Parallel
            return Optional.empty();
        }
        var t = (diff.y() * vB.x() - diff.x() * vB.y()) / fDenom;
        var s = (diff.y() * vA.x() - diff.x() * vA.y()) / fDenom;
        if (t < 0.0f || t > 1.0f || s < 0.0f || s > 1.0f) {
            // Lines crossing outside of segment
            return Optional.empty();
        } else {
            return Optional.of(start.add(vA.mult(t)));
        }
    }
}
