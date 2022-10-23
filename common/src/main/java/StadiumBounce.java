import com.fasterxml.jackson.annotation.JsonProperty;

public class StadiumBounce {

    public static Rs noBounced() {
        return new Rs(null, null);
    }

    public static Rs collided(Point pos, Point vec) {
        return new Rs(null, new Collision(pos, vec));
    }

    public static Rs scored(String team) {
        return new Rs(team, null);
    }


    record Rq(@JsonProperty("seg") Segment seg) implements Jsonisable {}

    record Rs(
            @JsonProperty("scoredTeam") String scoredTeam,
            @JsonProperty("collision") Collision collision
    ) implements Jsonisable {
        boolean didBounce() {
            return scoredTeam != null || collision != null;
        }
    }

    record Collision(
            @JsonProperty("pos") Point pos,
            @JsonProperty("vec") Point vec
    ) implements Jsonisable {}
}
