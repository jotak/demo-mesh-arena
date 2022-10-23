import com.fasterxml.jackson.annotation.JsonProperty;

public class BallShoot {
    record Rq(
            @JsonProperty("vec") Point vec,
            @JsonProperty("kind") String kind,
            @JsonProperty("playerID") String playerID
    ) implements Jsonisable {}
}
