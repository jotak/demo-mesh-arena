import com.fasterxml.jackson.annotation.JsonProperty;


record Point(
        @JsonProperty("x") double x,
        @JsonProperty("y") double y
) implements Jsonisable {
    public static final Point ZERO = new Point(0.0, 0.0);

    public Point add(Point other) {
        return new Point(x + other.x(), y + other.y());
    }

    public Point diff(Point other) {
        return new Point(x - other.x(), y - other.y());
    }

    public Point mult(double scalar) {
        return new Point(x * scalar, y * scalar);
    }

    public Point div(double scalar) {
        return new Point(x / scalar, y/ scalar);
    }

    public double size() {
        return Math.sqrt(x * x + y * y);
    }

    public double dist(Point other) {
        return diff(other).size();
    }

    public Point normalize() {
        var size = size();
        if (size != 0.0) {
            return div(size);
        }
        return ZERO;
    }

    public Point rotate(double angle) {
        var cos = Math.cos(angle);
        var sin = Math.sin(angle);
        return new Point(x * cos - y * sin, x * sin + y * cos);
    }

    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
