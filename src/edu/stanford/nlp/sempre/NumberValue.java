package edu.stanford.nlp.sempre;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

import fig.basic.LogInfo;

/**
 * Represents a numerical value (optionally comes with a unit).
 * In the future, might want to split this into an Integer version?
 *
 * @author Percy Liang
 */
public class NumberValue extends Value {
  @JsonProperty
  public final double value;
  @JsonProperty
  public final String unit;  // What measurement (e.g., "fb:en.meter" or unitless)

  public static final Pattern PATTERN = Pattern.compile("(P|PT)([0-9\\.]+)([mMSDHYW])");

  public static NumberValue parseDurationValue(String durationStr) {
    if(!PATTERN.matcher(durationStr).matches())
      return null;

    Matcher m = PATTERN.matcher(durationStr);
    if(m.find()) {
      boolean dailyValue = false;
      if(m.group(1).equals("PT"))
        dailyValue = true;

      String unitStr = m.group(3);
      String unit;
      switch (unitStr) {
        case "S":
          unit = "s";
          break;
        case "m":
          unit = "min";
          break;
        case "M":
          unit = dailyValue ? "min" : "month";
          break;
        case "H":
          unit = "h";
          break;
        case "D":
          unit = "day";
          break;
        case "W":
          unit = "week";
          break;
        case "Y":
          unit = "year";
          break;
        default:
          LogInfo.warnings("Got unknown unit %s", unitStr);
          return null;
      }

      try {
        return new NumberValue(Double.parseDouble(m.group(2)), unit);
      } catch(NumberFormatException e) {
        LogInfo.warnings("Cannot parse %s as a number", m.group(1));
        return null;
      }
    } else {
      LogInfo.warning("Cannot parse duration string");
      return null;
    }
  }

  public NumberValue(double value, String unit) {
    this.value = value;
    this.unit = unit;
  }

  @Override public int hashCode() { return Double.valueOf(value).hashCode(); }
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NumberValue that = (NumberValue) o;
    if (this.value != that.value) return false;  // Warning: doing exact equality checking
    if (!Objects.equals(this.unit, that.unit)) return false;
    return true;
  }

  @Override
  public String toString() {
    return this.value + (this.unit != null ? " " + this.unit : "");
  }
}
