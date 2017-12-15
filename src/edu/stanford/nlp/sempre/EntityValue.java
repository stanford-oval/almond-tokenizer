package edu.stanford.nlp.sempre;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EntityValue extends Value {
  @JsonProperty
  public final String value;
  @JsonProperty
  public final String display;

  public EntityValue(String value, String display) {
    this.value = value;
    this.display = display;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + value.hashCode();
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
    EntityValue other = (EntityValue) obj;
    if (!value.equals(other.value))
      return false;
    return true;
  }
}
