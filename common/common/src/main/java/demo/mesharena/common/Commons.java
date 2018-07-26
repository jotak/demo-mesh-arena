package demo.mesharena.common;

public final class Commons {

  public static final int UI_PORT = getIntEnv("MESHARENA_UI_PORT", 8080);
  public static final String UI_HOST = getStringEnv("MESHARENA_UI_HOST", "localhost");
  public static final int BALL_PORT = getIntEnv("MESHARENA_BALL_PORT", 8081);
  public static final String BALL_HOST = getStringEnv("MESHARENA_BALL_HOST", "localhost");
  public static final int STADIUM_PORT = getIntEnv("MESHARENA_STADIUM_PORT", 8082);
  public static final String STADIUM_HOST = getStringEnv("MESHARENA_STADIUM_HOST", "localhost");

  private Commons() {
  }

  public static String getStringEnv(String varname, String def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      System.out.println(varname + " = " + html(val));
      return html(val);
    }
  }

  public static int getIntEnv(String varname, int def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      return Integer.parseInt(val);
    }
  }

  public static double getDoubleEnv(String varname, double def) {
    String val = System.getenv(varname);
    if (val == null || val.isEmpty()) {
      return def;
    } else {
      return Double.parseDouble(val);
    }
  }

  public static String html(String str) {
    StringBuilder out = new StringBuilder();
    for (char c : str.toCharArray()) {
      if (!Character.isLetterOrDigit(c)) {
        out.append(String.format("&#x%x;", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
