package edu.stanford.nlp.sempre;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import edu.stanford.nlp.util.logging.Redwood;

public class LocationLexicon extends AbstractLexicon<LocationValue> {
  private static final Redwood.RedwoodChannels log = Redwood.channels(LocationLexicon.class);

  @JsonIgnoreProperties({ "boundingbox", "licence" })
  private static class NominatimEntry {
    @JsonProperty
    public String category;
    @JsonProperty
    public String display_name;
    @JsonProperty
    public String icon;
    @JsonProperty
    public double importance;
    @JsonProperty
    public double lat;
    @JsonProperty
    public double lon;
    @JsonProperty
    public String osm_id;
    @JsonProperty
    public String osm_type;
    @JsonProperty
    public String place_id;
    @JsonProperty
    public String place_rank;
    @JsonProperty
    public String type;
  }

  private static final String URL_TEMPLATE = "http://open.mapquestapi.com/nominatim/v1/search.php?key=%s&format=jsonv2&accept-language=%s&limit=5&q=%s";
  private static final Map<String, LocationLexicon> instances = new HashMap<>();

  private final String languageTag;

  private LocationLexicon(String languageTag) {
    this.languageTag = languageTag;
  }

  public synchronized static LocationLexicon getForLanguage(String languageTag) {
    LocationLexicon instance = instances.get(languageTag);
    if (instance == null) {
      instance = new LocationLexicon(languageTag);
      instances.put(languageTag, instance);
    }
    return instance;
  }

  private static <E1, E2> Collection<E2> map(Collection<E1> from, Function<E1, E2> f) {
    Collection<E2> to = new ArrayList<>();
    for (E1 e1 : from) {
      E2 e2 = f.apply(e1);
      to.add(e2);
    }
    return to;
  }

  @Override
  protected Collection<Entry<LocationValue>> doLookup(String rawPhrase) {
    try {
      String mapQuestKey = System.getProperty("almond.mapQuestKey");
      URL url = new URL(String.format(URL_TEMPLATE, mapQuestKey, languageTag, URLEncoder.encode(rawPhrase, "utf-8")));
      if (verbose >= 3)
        log.debugf("LocationLexicon HTTP call to %s", url);

      URLConnection connection = url.openConnection();
      connection.setRequestProperty("User-Agent", "Almond Tokenizer/2.1 JavaSE/1.8");
      connection.setUseCaches(true);

      try (Reader reader = new InputStreamReader(connection.getInputStream())) {
        return map(Json.getMapper().reader().withType(new TypeReference<List<NominatimEntry>>() {
        }).readValue(reader), (NominatimEntry entry) -> {
          int rank = Integer.parseInt(entry.place_rank);
          String canonical = entry.display_name.toLowerCase().replaceAll("[,\\s+]", " ");
          return new Entry<>("LOCATION", new LocationValue(entry.lat, entry.lon, entry.display_name, rank),
              canonical);
        });
      }
    } catch (IOException e) {
      log.errf("Failed to contact location server: %s", e.getMessage());
      return Collections.emptyList();
    }
  }
}
