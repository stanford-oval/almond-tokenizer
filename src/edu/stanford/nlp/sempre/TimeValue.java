package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by joberant on 1/23/15.
 * Value for representing time
 */
public class TimeValue {
  @JsonProperty
  public final int hour;
  @JsonProperty
  public final int minute;
  @JsonProperty
  public final double second;

  public TimeValue(int hour, int minute, double second) {
    if (hour > 23 || hour < 0) throw new RuntimeException("Illegal hour: " + hour);
    if (minute > 59 || minute < 0) throw new RuntimeException("Illegal minute: " + minute);
    if (second > 59 || second < 0) throw new RuntimeException("Illegal second: " + second);
    this.hour = hour;
    this.minute = minute;
    this.second = second;
  }

  @Override
  public String toString() {
    return this.hour + ":" + this.minute + ":" + this.second;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TimeValue timeValue = (TimeValue) o;
    if (hour != timeValue.hour) return false;
    if (minute != timeValue.minute) return false;
    if (second != timeValue.second) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = hour;
    result = 31 * result + minute;
    result = (int)(31 * result + second);
    return result;
  }
}
