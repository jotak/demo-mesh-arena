import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.SecureRandom;


record ArenaInfo(
        @JsonProperty("defendZoneTopLeft") Point defendZoneTopLeft,
        @JsonProperty("defendZoneBottomRight") Point defendZoneBottomRight,
        @JsonProperty("goal") Point goal
) implements Jsonisable {
    public Point randomDefendPoint(SecureRandom rnd) {
        var diag = defendZoneBottomRight.diff(defendZoneTopLeft);
        return new Point(rnd.nextInt((int) diag.x()), rnd.nextInt((int) diag.y()))
                .add(defendZoneTopLeft);
    }
}
