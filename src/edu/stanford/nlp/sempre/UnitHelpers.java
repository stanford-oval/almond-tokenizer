package edu.stanford.nlp.sempre;

import java.util.*;

import com.google.common.collect.Sets;

class UnitHelpers {
  private UnitHelpers() {
  }

  private static final Set<String> TIME_UNITS = Sets.newHashSet("ms", "s", "min", "h", "day", "week", "month", "year");
  private static final Set<String> TEMP_UNITS = Sets.newHashSet("C", "F");
  private static final Set<String> LENGTH_UNITS = Sets.newHashSet("m", "km", "mm", "cm", "mi", "in", "ft");
  private static final Set<String> SPEED_UNITS = Sets.newHashSet("mps", "kmph", "mph");
  private static final Set<String> WEIGHT_UNITS = Sets.newHashSet("kg", "g", "lb", "oz");
  private static final Set<String> PRESSURE_UNITS = Sets.newHashSet("Pa", "bar", "psi", "mmHg", "inHg", "atm");
  private static final Set<String> ENERGY_UNITS = Sets.newHashSet("kcal", "kJ");
  private static final Set<String> HEARTRATE_UNITS = Collections.singleton("bpm");
  private static final Set<String> FILESIZE_UNITS = Sets.newHashSet("byte", "KB", "KiB", "MB", "MiB", "GB", "GiB", "TB",
      "TiB");
  private static final Map<String, Set<String>> ALLOWED_UNITS = new HashMap<>();
  private static final Set<String> ALL_UNITS = new HashSet<>();
  static {
    ALLOWED_UNITS.put("ms", TIME_UNITS);
    ALLOWED_UNITS.put("C", TEMP_UNITS);
    ALLOWED_UNITS.put("m", LENGTH_UNITS);
    ALLOWED_UNITS.put("mps", SPEED_UNITS);
    ALLOWED_UNITS.put("kg", WEIGHT_UNITS);
    ALLOWED_UNITS.put("mmHg", PRESSURE_UNITS);
    ALLOWED_UNITS.put("kcal", ENERGY_UNITS);
    ALLOWED_UNITS.put("bpm", HEARTRATE_UNITS);
    ALLOWED_UNITS.put("byte", FILESIZE_UNITS);

    ALLOWED_UNITS.forEach((type, units) -> {
      for (String unit : units) {
        ALL_UNITS.add(unit);
      }
    });
  }

  static boolean isUnit(String unit) {
    return ALL_UNITS.contains(unit);
  }

  static boolean isTimeUnit(String unit) {
    return TIME_UNITS.contains(unit);
  }
}
