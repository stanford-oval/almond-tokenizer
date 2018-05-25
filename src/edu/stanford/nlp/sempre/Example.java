package edu.stanford.nlp.sempre;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

  // Expected type/context
  public final String expected;

  //// Information after preprocessing (e.g., tokenization, POS tagging, NER, syntactic parsing, etc.).
  public LanguageInfo languageInfo = null;

  public static class Builder {
    private String id;
    private String utterance;
    private String expected;
    private LanguageInfo languageInfo;

    public Builder setId(String id) {
      this.id = id;
      return this;
    }

    public Builder setUtterance(String utterance) {
      this.utterance = utterance;
      return this;
    }

    public Builder setExpected(String expect) {
      this.expected = expect;
      return this;
    }

    public Builder setLanguageInfo(LanguageInfo languageInfo) {
      this.languageInfo = languageInfo;
      return this;
    }

    public Builder withExample(Example ex) {
      setId(ex.id);
      setUtterance(ex.utterance);
      return this;
    }

    public Example createExample() {
      return new Example(id, utterance, expected, languageInfo);
    }
  }

  @JsonCreator
  public Example(@JsonProperty("id") String id,
      @JsonProperty("utterance") String utterance,
      @JsonProperty("expected") String expected,
      @JsonProperty("languageInfo") LanguageInfo languageInfo) {
    this.id = id;
    this.utterance = utterance;
    this.expected = expected;
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
    this.languageInfo = analyzer.analyze(this.utterance, this.expected);
  }
}
