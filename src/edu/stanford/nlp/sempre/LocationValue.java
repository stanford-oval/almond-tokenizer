package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LocationValue {
  @JsonProperty
  private final double latitude;
  @JsonProperty
  private final double longitude;
  @JsonProperty
  private final String display;

  public LocationValue(double latitude, double longitude) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.display = null;
  }

  public LocationValue(double latitude, double longitude, String display) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.display = display;
  }

  @Override
  public String toString() {
    return "[Lat: " + this.latitude + ", Lon: + " + this.longitude + " (" + this.display + ")]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    long temp;
    temp = Double.doubleToLongBits(Math.round(latitude * 100));
    result = prime * result + (int) (temp ^ (temp >>> 32));
    temp = Double.doubleToLongBits(Math.round(longitude * 100));
    result = prime * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LocationValue other = (LocationValue) obj;
    if (Math.abs(latitude - other.latitude) > 0.001)
      return false;
    if (Math.abs(longitude - other.longitude) > 0.001)
      return false;
    return true;
  }

}
