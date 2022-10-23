
public class Style {
  private static final boolean NO_TRANSITION = Commons.getBoolEnv("NO_TRANSITION", false);

  private String position;
  private String dimensions;
  private String zIndex;
  private String image;
  private String bgColor;
  private String transition;
  private String transform;
  private String brightness;
  private String brightHueSat;
  private String other;

  public Style position(int top, int left) {
    this.position = "top: " + top + "px; left: " + left + "px; ";
    return this;
  }

  public Style dimensions(int w, int h) {
    this.dimensions = "height: " + h + "px; width: " + w + "px; ";
    return this;
  }

  public Style zIndex(int zIndex) {
    this.zIndex = "z-index: " + zIndex + ";";
    return this;
  }

  public Style image(String img) {
    this.image = "background-image: url(" + img + ");";
    return this;
  }

  public Style bgColor(String color) {
    this.bgColor = "background-color: " + color + ";";
    return this;
  }

  public Style transition(int delta) {
    this.transition = "transition: top " + delta + "ms, left " + delta + "ms;";
    return this;
  }

  public Style rotate(double angle) {
    this.transform = "transform: rotate(" + (int)(angle*180/Math.PI) + "deg);";
    return this;
  }

  public Style brightness(int brightness) {
    this.brightHueSat = null;
    this.brightness = "filter: brightness(" + brightness + "%); -webkit-filter: brightness(" + brightness + "%);";
    return this;
  }

  public Style brightHueSat(int brightness, int hue, int saturation, int sepia) {
    this.brightness = null;
    this.brightHueSat = ("filter: brightness(" + brightness + "%) sepia(" + sepia + "%) hue-rotate(" + hue + "deg) saturate(" + saturation + "%);"
      + "-webkit-filter: brightness(" + brightness + "%) sepia(" + sepia + "%) hue-rotate(" + hue + "deg) saturate(" + saturation + "%);");
    return this;
  }

  public Style other(String other) {
    this.other = other;
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("position: absolute; ");
    if (position != null) {
      sb.append(position);
    }
    if (dimensions != null) {
      sb.append(dimensions);
    }
    if (zIndex != null) {
      sb.append(zIndex);
    }
    if (image != null) {
      sb.append(image);
    }
    if (bgColor != null) {
      sb.append(bgColor);
    }
    if (transition != null && !NO_TRANSITION) {
      sb.append(transition);
    }
    if (transform != null) {
      sb.append(transform);
    }
    if (brightness != null) {
      sb.append(brightness);
    }
    if (brightHueSat != null) {
      sb.append(brightHueSat);
    }
    if (other != null) {
      sb.append(other);
    }
    return sb.toString();
  }
}
