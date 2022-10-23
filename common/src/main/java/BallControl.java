import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.SecureRandom;

public class BallControl {

    public static Rs ballKept(Point pos) {
        return new Rs(pos, true, false);
    }

    public static Rs ballTaken(Point pos) {
        return new Rs(pos, true, true);
    }

    public static Rs ballMissed(Point pos) {
        return new Rs(pos, false, false);
    }


    record Rq(
            @JsonProperty("pos") Point pos,
            @JsonProperty("player") Player player
    ) implements Jsonisable {}

    record Rs(
            @JsonProperty("pos") Point pos,
            @JsonProperty("success") boolean success,
            @JsonProperty("takesBall") boolean takesBall
    ) implements Jsonisable {}

    record Player(
            @JsonProperty("id") String id,
            @JsonProperty("skill") int skill,
            @JsonProperty("name") String name,
            @JsonProperty("team") String team
    ) implements Jsonisable {
        boolean takesBall(Player controllingPlayer, SecureRandom rnd) {
            return controllingPlayer == null || rnd.nextInt(2 * controllingPlayer.skill + skill) < skill;
        }

        public Player withSkill(int skill) {
            return new Player(id, skill, name, team);
        }
    }

    record Collision(
            @JsonProperty("pos") Point pos,
            @JsonProperty("vec") Point vec
    ) implements Jsonisable {}
}
