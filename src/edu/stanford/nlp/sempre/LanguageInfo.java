package edu.stanford.nlp.sempre;

import java.util.*;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.Joiner;

import fig.basic.IntPair;
import fig.basic.MemUsage;

/**
 * Represents an linguistic analysis of a sentence (provided by some LanguageAnalyzer).
 *
 * @author akchou
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LanguageInfo implements MemUsage.Instrumented {

  // Tokenization of input.
  @JsonProperty
  public final List<String> tokens;
  @JsonProperty
  public final List<String> lemmaTokens;  // Lemmatized version

  // Syntactic information from JavaNLP.
  @JsonProperty
  public final List<String> posTags;  // POS tags
  @JsonProperty
  public final List<String> nerTags;  // NER tags
  @JsonProperty
  public final List<String> nerValues;  // NER values (contains times, dates, etc.)
  public final List<String> nerTokens; // NER tag if NER value != null, else token

  private Map<String, IntPair> lemmaSpans;
  private Set<String> lowercasedSpans;
  private Set<String> nerSpans;

  public LanguageInfo() {
    this(new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>());
  }

  @JsonCreator
  public LanguageInfo(@JsonProperty("tokens") List<String> tokens,
      @JsonProperty("lemmaTokens") List<String> lemmaTokens,
      @JsonProperty("posTags") List<String> posTags,
      @JsonProperty("nerTags") List<String> nerTags,
      @JsonProperty("nerValues") List<String> nerValues) {
    this.tokens = tokens;
    this.lemmaTokens = lemmaTokens;
    this.posTags = posTags;
    this.nerTags = nerTags;
    this.nerValues = nerValues;
    this.nerTokens = new ArrayList<>();

    computeNerTokens();
  }

  public void computeNerTokens() {
    nerTokens.clear();

    String previousTag = null;
    for (int i = 0; i < tokens.size(); i++) {
      String current;

      if (nerValues.get(i) != null) {
        current = nerTags.get(i);
        if (current.equals(previousTag))
          continue;
        previousTag = nerTags.get(i);
      } else {
        current = tokens.get(i);
        previousTag = null;
      }
      nerTokens.add(current);
    }
  }

  // Return a string representing the tokens between start and end.
  public String phrase(int start, int end) {
    return sliceSequence(tokens, start, end);
  }
  public String lemmaPhrase(int start, int end) {
    return sliceSequence(lemmaTokens, start, end);
  }
  public String posSeq(int start, int end) {
    return sliceSequence(posTags, start, end);
  }

  public String canonicalPosSeq(int start, int end) {
    if (start >= end) throw new RuntimeException("Bad indices, start=" + start + ", end=" + end);
    if (end - start == 1) return LanguageUtils.getCanonicalPos(posTags.get(start));
    StringBuilder out = new StringBuilder();
    for (int i = start; i < end; i++) {
      if (out.length() > 0) out.append(' ');
      out.append(LanguageUtils.getCanonicalPos(posTags.get(i)));
    }
    return out.toString();
  }
  public String nerSeq(int start, int end) {
    return sliceSequence(nerTags, start, end);
  }

  public String nerPhrase(int start, int end) {
    return sliceSequence(nerTokens, start, end);
  }

  private static String sliceSequence(List<String> items,
      int start,
      int end) {
    if (start >= end) throw new RuntimeException("Bad indices, start=" + start + ", end=" + end);
    if (end - start == 1) return items.get(start);
    StringBuilder out = new StringBuilder();
    for (int i = start; i < end; i++) {
      if (out.length() > 0) out.append(' ');
      out.append(items.get(i));
    }
    return out.toString();
  }

  // If all the tokens in [start, end) have the same nerValues, but not
  // start - 1 and end + 1 (in other words, [start, end) is maximal), then return
  // the normalizedTag.  Example: queryNerTag = "DATE".
  public String getNormalizedNerSpan(String queryTag, int start, int end) {
    String value = nerValues.get(start);
    if (value == null) return null;
    if (!queryTag.equals(nerTags.get(start))) return null;
    if (start - 1 >= 0 && value.equals(nerValues.get(start - 1))) return null;
    if (end < nerValues.size() && value.equals(nerValues.get(end))) return null;
    for (int i = start + 1; i < end; i++)
      if (!value.equals(nerValues.get(i))) return null;
    value = omitComparative(value);
    return value;
  }

  public boolean isMaximalNerSpan(String queryTag, int start, int end) {
    for (int i = start; i < end; i++) {
      if (!queryTag.equals(nerTags.get(i)))
        return false;
    }
    if (start > 0 && queryTag.equals(nerTags.get(start - 1)))
      return false;
    if (end < nerTags.size() && queryTag.equals(nerTags.get(end)))
      return false;
    return true;
  }

  private String omitComparative(String value) {
    if (value.startsWith("<=") || value.startsWith(">="))
      return value.substring(2);
    if (value.startsWith("<") || value.startsWith(">"))
      return value.substring(1);
    return value;
  }

  public String getCanonicalPos(int index) {
    if (index == -1) return "OUT";
    return LanguageUtils.getCanonicalPos(posTags.get(index));
  }

  public boolean equalTokens(LanguageInfo other) {
    if (tokens.size() != other.tokens.size())
      return false;
    for (int i = 0; i < tokens.size(); ++i) {
      if (!tokens.get(i).equals(other.tokens.get(i)))
        return false;
    }
    return true;
  }

  public boolean equalLemmas(LanguageInfo other) {
    if (lemmaTokens.size() != other.lemmaTokens.size())
      return false;
    for (int i = 0; i < tokens.size(); ++i) {
      if (!lemmaTokens.get(i).equals(other.lemmaTokens.get(i)))
        return false;
    }
    return true;
  }

  public int numTokens() {
    return tokens.size();
  }

  public LanguageInfo remove(int startIndex, int endIndex) {

    if (startIndex > endIndex || startIndex < 0 || endIndex > numTokens())
      throw new RuntimeException("Illegal start or end index, start: " + startIndex + ", end: " + endIndex + ", info size: " + numTokens());

    LanguageInfo res = new LanguageInfo();
    for (int i = 0; i < numTokens(); ++i) {
      if (i < startIndex || i >= endIndex) {
        res.tokens.add(this.tokens.get(i));
        res.lemmaTokens.add(this.lemmaTokens.get(i));
        res.nerTags.add(this.nerTags.get(i));
        res.nerValues.add(this.nerValues.get(i));
        res.posTags.add(this.posTags.get(i));
      }
    }
    return res;
  }

  /**
   * Static methods with langauge utilities
   * @author jonathanberant
   *
   */
  public static class LanguageUtils {

    public static boolean sameProperNounClass(String noun1, String noun2) {
      if ((noun1.equals("NNP") || noun1.equals("NNPS")) &&
          (noun2.equals("NNP") || noun2.equals("NNPS")))
        return true;
      return false;
    }

    public static boolean isProperNoun(String pos) {
      return pos.startsWith("NNP");
    }

    public static boolean isSuperlative(String pos) { return pos.equals("RBS") || pos.equals("JJS"); }
    public static boolean isComparative(String pos) { return pos.equals("RBR") || pos.equals("JJR"); }


    public static boolean isEntity(LanguageInfo info, int i) {
      return isProperNoun(info.posTags.get(i)) || !(info.nerTags.get(i).equals("O"));
    }

    public static boolean isNN(String pos) {
      return pos.startsWith("NN") && !pos.startsWith("NNP");
    }

    public static boolean isContentWord(String pos) {
      return (pos.startsWith("N") || pos.startsWith("V") || pos.startsWith("J"));
    }

    public static String getCanonicalPos(String pos) {
      if (pos.startsWith("N")) return "N";
      if (pos.startsWith("V")) return "V";
      if (pos.startsWith("W")) return "W";
      return pos;
    }

  }

  @Override
  public long getBytes() {
    return MemUsage.objectSize(MemUsage.pointerSize * 2) + MemUsage.getBytes(tokens) + MemUsage.getBytes(lemmaTokens)
        + MemUsage.getBytes(posTags) + MemUsage.getBytes(nerTags) + MemUsage.getBytes(nerValues)
        + MemUsage.getBytes(lemmaSpans);
  }

  public static boolean isContentWord(String pos) {
    return pos.equals("NN") || pos.equals("NNS") ||
            (pos.startsWith("V") && !pos.equals("VBD-AUX")) ||
            pos.startsWith("J");
  }
}
