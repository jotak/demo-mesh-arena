package demo.mesharena.common;

public final class Commons {

  public static final int UI_PORT = getIntEnv("UI_PORT", 8080);
  public static final String UI_HOST = getStringEnv("UI_HOST", "localhost");
  public static final int BALL_PORT = getIntEnv("BALL_PORT", 8081);
  public static final String BALL_HOST = getStringEnv("BALL_HOST", "localhost");
  public static final int STADIUM_PORT = getIntEnv("STADIUM_PORT", 8082);
  public static final String STADIUM_HOST = getStringEnv("STADIUM_HOST", "localhost");

  private Commons() {
  }

  private static String getStringEnv(String varname, String def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      return val;
    }
  }

  private static int getIntEnv(String varname, int def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      return Integer.parseInt(val);
    }
  }

}
