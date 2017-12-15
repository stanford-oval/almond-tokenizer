package edu.stanford.nlp.sempre;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DateValue extends Value {
  @JsonProperty
  public final int year;
  @JsonProperty
  public final int month;
  @JsonProperty
  public final int day;
  @JsonProperty
  public final int hour;
  @JsonProperty
  public final int minute;
  @JsonProperty
  public final double second;

  private static final Pattern PATTERN = Pattern
      .compile(
          "-?([0-9X*]{4})?-?([0-9X*]{2})?-?([0-9X*]{2})?(?:T([0-9X*]{2})?:?([0-9X*]{2})?:?([0-9X*]{2}(?:\\.[0-9]+)?)?)?Z?");

  // Format: YYYY-MM-DD (from Freebase).
  // Return null if it's not a valid date string.
  public static DateValue parseDateValue(String dateStr) {
    if (dateStr.equals("PRESENT_REF")) return now();

    Matcher matcher = PATTERN.matcher(dateStr);
    if (!matcher.matches())
      return null;

    int year = -1, month = -1, day = -1;
    int hour = 0, minute = 0;
    double second = 0;
    boolean isBC = dateStr.startsWith("-");

    String[] dateParts;
    String[] timeParts = null;

    if (dateStr.indexOf('T') != -1) {
      timeParts = new String[] { matcher.group(4), matcher.group(5), matcher.group(6) };
    }

    dateParts = new String[] { matcher.group(1), matcher.group(2), matcher.group(3) };
    if (dateParts.length > 3)
      throw new RuntimeException("Date has more than 3 parts: " + dateStr);

    if (dateParts.length >= 1) year = parseIntRobust(dateParts[0]) * (isBC ? -1 : 1);
    if (dateParts.length >= 2) month = parseIntRobust(dateParts[1]);
    if (dateParts.length >= 3) day = parseIntRobust(dateParts[2]);

    if (timeParts != null) {
      if (timeParts.length >= 1) hour = parseIntRobust(timeParts[0]);
      if (timeParts.length >= 2) minute = parseIntRobust(timeParts[1]);
      if (timeParts.length >= 3) second = parseDoubleRobust(timeParts[2]);

      return new DateValue(year, month, day, hour, minute, second);
    } else {
      return new DateValue(year, month, day);
    }
  }

  private static int parseIntRobust(String i) {
    if (i == null)
      return -1;
    int val;
    try {
      val = Integer.parseInt(i);
    } catch (NumberFormatException ex) {
      val = -1;
    }
    return val;
  }

  private static double parseDoubleRobust(String i) {
    if (i == null)
      return 0;
    try {
      return Double.parseDouble(i);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public static DateValue now() {
    Calendar cal = Calendar.getInstance();
    int year = cal.get(Calendar.YEAR);
    int month = cal.get(Calendar.MONTH)+1;
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int hour = cal.get(Calendar.HOUR_OF_DAY);
    int minute = cal.get(Calendar.MINUTE);
    double second = cal.get(Calendar.SECOND) + 0.001 * cal.get(Calendar.MILLISECOND);
    return new DateValue(year, month, day, hour, minute, second);
  }

  public DateValue(int year, int month, int day) {
    this.year = year;
    this.month = month;
    this.day = day;
    this.hour = 0;
    this.minute = 0;
    this.second = 0;
  }

  public DateValue(int year, int month, int day, int hour, int minute, double second) {
    this.year = year;
    this.month = month;
    this.day = day;
    this.hour = hour;
    this.minute = minute;
    this.second = second;
  }

  @Override public int hashCode() {
    int hash = 0x7ed55d16;
    hash = hash * 0xd3a2646c + year;
    hash = hash * 0xd3a2646c + month;
    hash = hash * 0xd3a2646c + day;
    hash = hash * 0xd3a2646c + hour;
    hash = hash * 0xd3a2646c + minute;
    hash = hash * 0xd3a2646c + (new Double(second)).hashCode();
    return hash;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DateValue that = (DateValue) o;
    if (this.year != that.year) return false;
    if (this.month != that.month) return false;
    if (this.day != that.day) return false;
    if (this.hour != that.hour) return false;
    if (this.minute != that.minute) return false;
    if (this.second != that.second) return false;
    return true;
  }
}
