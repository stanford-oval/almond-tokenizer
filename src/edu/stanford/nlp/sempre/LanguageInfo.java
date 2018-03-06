package edu.stanford.nlp.sempre;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an linguistic analysis of a sentence (provided by some LanguageAnalyzer).
 *
 * @author akchou
 */
public class LanguageInfo {

  // Tokenization of input.
  public final List<String> tokens;
  public final List<String> lemmaTokens;  // Lemmatized version

  // Syntactic information from JavaNLP.
  public final List<String> posTags;  // POS tags
  public final List<String> nerTags;  // NER tags
  public final List<String> nerValues;  // NER values (contains times, dates, etc.)

  public LanguageInfo() {
    this(new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>(),
        new ArrayList<String>());
  }

  public LanguageInfo(List<String> tokens,
      List<String> lemmaTokens,
      List<String> posTags,
      List<String> nerTags,
      List<String> nerValues) {
    this.tokens = tokens;
    this.lemmaTokens = lemmaTokens;
    this.posTags = posTags;
    this.nerTags = nerTags;
    this.nerValues = nerValues;
  }

  public int numTokens() {
    return tokens.size();
  }
}
