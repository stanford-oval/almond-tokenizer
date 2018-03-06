package edu.stanford.nlp.sempre;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fig.basic.LogInfo;

/**
 * An example corresponds roughly to an input-output pair, the basic unit which
 * we make predictions on.  The Example object stores both the input,
 * preprocessing, and output of the parser.
 *
 * @author Percy Liang
 * @author Roy Frostig
 */
public class Example {
  //// Information from the input file.

  // Unique identifier for this example.
  public final String id;

  // Input utterance
  public final String utterance;

  //// Information after preprocessing (e.g., tokenization, POS tagging, NER, syntactic parsing, etc.).
  public LanguageInfo languageInfo = null;

  public static class Builder {
    private String id;
    private String utterance;
    private LanguageInfo languageInfo;

    public Builder setId(String id) { this.id = id; return this; }
    public Builder setUtterance(String utterance) { this.utterance = utterance; return this; }

    public Builder setLanguageInfo(LanguageInfo languageInfo) { this.languageInfo = languageInfo; return this; }
    public Builder withExample(Example ex) {
      setId(ex.id);
      setUtterance(ex.utterance);
      return this;
    }
    public Example createExample() {
      return new Example(id, utterance, languageInfo);
    }
  }

  @JsonCreator
  public Example(@JsonProperty("id") String id,
                 @JsonProperty("utterance") String utterance,
                 @JsonProperty("languageInfo") LanguageInfo languageInfo) {
    this.id = id;
    this.utterance = utterance;
    this.languageInfo = languageInfo;
  }

  // Accessors
  public String getId() { return id; }
  public String getUtterance() { return utterance; }
  public int numTokens() { return languageInfo.tokens.size(); }

  // Return a string representing the tokens between start and end.
  public List<String> getTokens() { return languageInfo.tokens; }

  public List<String> getLemmaTokens() {
    return languageInfo.lemmaTokens;
  }
  public String token(int i) { return languageInfo.tokens.get(i); }

  public String lemmaToken(int i) {
    return languageInfo.lemmaTokens.get(i);
  }
  public String posTag(int i) { return languageInfo.posTags.get(i); }

  public void preprocess(CoreNLPAnalyzer analyzer) {
    this.languageInfo = analyzer.analyze(this.utterance);
  }

  public void log() {
    LogInfo.begin_track("Example: %s", utterance);
    LogInfo.logs("Tokens: %s", getTokens());
    LogInfo.logs("Lemmatized tokens: %s", getLemmaTokens());
    LogInfo.logs("POS tags: %s", languageInfo.posTags);
    LogInfo.logs("NER tags: %s", languageInfo.nerTags);
    LogInfo.logs("NER values: %s", languageInfo.nerValues);
    LogInfo.end_track();
  }
}
