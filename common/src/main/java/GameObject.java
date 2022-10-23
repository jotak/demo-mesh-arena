import com.fasterxml.jackson.annotation.JsonProperty;

record GameObject(
        @JsonProperty("id") String id,
        @JsonProperty("style") String style,
        @JsonProperty("x") double x,
        @JsonProperty("y") double y,
        @JsonProperty("text") String text,
        @JsonProperty("playerRef") PlayerRef playerRef
) implements Jsonisable {
    public GameObject withStyle(String style) {
        return new GameObject(id, style, x, y, text, playerRef);
    }
    public GameObject withX(double x) {
        return new GameObject(id, style, x, y, text, playerRef);
    }
    public GameObject withY(double y) {
        return new GameObject(id, style, x, y, text, playerRef);
    }
    public GameObject withText(String text) {
        return new GameObject(id, style, x, y, text, playerRef);
    }
    public GameObject withPlayerRef(PlayerRef playerRef) {
        return new GameObject(id, style, x, y, text, playerRef);
    }
}

record PlayerRef(
        @JsonProperty("name") String name,
        @JsonProperty("ip") String ip,
        @JsonProperty("port") int port
) implements Jsonisable {
    public PlayerRef withName(String name) {
        return new PlayerRef(name, ip, port);
    }
}
