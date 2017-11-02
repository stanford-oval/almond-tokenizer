package edu.stanford.nlp.sempre.thingtalk;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.stanford.nlp.sempre.Json;
import fig.basic.LogInfo;

public class EntityLexicon extends AbstractLexicon<TypedStringValue> {
  private static final Map<String, EntityLexicon> instances = new HashMap<>();

  private final String languageTag;

  private static class ThingpediaEntityLookupResult {
    @JsonProperty
    public String result;
    @JsonProperty
    public List<ThingpediaEntityEntry> data;
  }
  private static class ThingpediaEntityEntry {
    @JsonProperty
    public String name;
    @JsonProperty
    public String value;
    @JsonProperty
    public String canonical;
    @JsonProperty
    public String type;
  }

  private EntityLexicon(String languageTag) {
    this.languageTag = languageTag;
  }

  public synchronized static EntityLexicon getForLanguage(String languageTag) {
    EntityLexicon instance = instances.get(languageTag);
    if (instance == null) {
      instance = new EntityLexicon(languageTag);
      instances.put(languageTag, instance);
    }
    return instance;
  }

  public synchronized static void clearAllCaches() {
    for (EntityLexicon e : instances.values())
      e.clear();
  }

  private static final String URL_TEMPLATE = "https://thingpedia.stanford.edu/thingpedia/api/entities/lookup?locale=%s&q=%s";

  private static <E1, E2> Collection<E2> map(Collection<E1> from, Function<E1, E2> f) {
    Collection<E2> to = new ArrayList<>();
    for (E1 e : from)
      to.add(f.apply(e));
    return to;
  }

  @Override
  protected Collection<Entry<TypedStringValue>> doLookup(String rawPhrase) {
    String token = LexiconUtils.preprocessRawPhrase(rawPhrase);
    if (token == null)
      return Collections.emptySet();
  
    Collection<Entry<TypedStringValue>> entries = new LinkedList<>();
    
    try {
      URL url = new URL(String.format(URL_TEMPLATE, languageTag, URLEncoder.encode(rawPhrase, "utf-8")));
      if (opts.verbose >= 3)
        LogInfo.logs("EntityLexicon HTTP call to %s", url);

      URLConnection connection = url.openConnection();

      try (Reader reader = new InputStreamReader(connection.getInputStream())) {
        return map(Json.readValueHard(reader, ThingpediaEntityLookupResult.class).data,
            (ThingpediaEntityEntry entry) -> {
          String type = "Entity(" + entry.type + ")";
          return new Entry<>("GENERIC_ENTITY_" + entry.type,
                  new TypedStringValue(type, entry.value, entry.name),
              entry.canonical);
        });
      }
    } catch (IOException e) {
      LogInfo.logs("Exception during entity lookup: %s", e.getMessage());
    }
    return entries;
  }
}
