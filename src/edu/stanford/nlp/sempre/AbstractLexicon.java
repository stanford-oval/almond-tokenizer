package edu.stanford.nlp.sempre;

import java.util.Collection;

import edu.stanford.nlp.util.logging.Redwood;

public abstract class AbstractLexicon<E extends Value> {
  static final int verbose = 0;
  private static final Redwood.RedwoodChannels log = Redwood.channels(AbstractLexicon.class);

  public static class Entry<E extends Value> {
    public final String nerTag;
    public final E value;
    public final String rawPhrase;

    public Entry(String nerTag, E value, String rawPhrase) {
      this.nerTag = nerTag;
      this.value = value;
      this.rawPhrase = rawPhrase;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((value == null) ? 0 : value.hashCode());
      result = prime * result + ((nerTag == null) ? 0 : nerTag.hashCode());
      result = prime * result + ((rawPhrase == null) ? 0 : rawPhrase.hashCode());
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
      Entry<?> other = (Entry<?>) obj;
      if (value == null) {
        if (other.value != null)
          return false;
      } else if (!value.equals(other.value))
        return false;
      if (nerTag == null) {
        if (other.nerTag != null)
          return false;
      } else if (!nerTag.equals(other.nerTag))
        return false;
      if (rawPhrase == null) {
        if (other.rawPhrase != null)
          return false;
      } else if (!rawPhrase.equals(other.rawPhrase))
        return false;
      return true;
    }
  }

  private final GenericObjectCache<String, Collection<Entry<E>>> cache = new GenericObjectCache<>(256);

  public void clear() {
    cache.clear();
  }

  protected abstract Collection<Entry<E>> doLookup(String rawPhrase);

  public Collection<Entry<E>> lookup(String rawPhrase) {
    if (verbose >= 2)
      log.debugf("AbstractLexicon.lookup %s", rawPhrase);
    Collection<Entry<E>> fromCache = cache.hit(rawPhrase);
    if (fromCache != null) {
      if (verbose >= 3)
        log.debugf("AbstractLexicon.cacheHit");
      return fromCache;
    }
    if (verbose >= 3)
      log.debugf("AbstractLexicon.cacheMiss");

    fromCache = doLookup(rawPhrase);
    // cache location lookups forever
    // if memory pressure occcurs, the cache will automatically
    // downsize itself
    cache.store(rawPhrase, fromCache, Long.MAX_VALUE);
    return fromCache;
  }
}

