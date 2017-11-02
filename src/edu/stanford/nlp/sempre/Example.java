package edu.stanford.nlp.sempre;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.Joiner;

import fig.basic.Evaluation;
import fig.basic.LogInfo;

/**
 * An example corresponds roughly to an input-output pair, the basic unit which
 * we make predictions on.  The Example object stores both the input,
 * preprocessing, and output of the parser.
 *
 * @author Percy Liang
 * @author Roy Frostig
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Example {
  //// Information from the input file.

  // Unique identifier for this example.
  @JsonProperty public final String id;

  // Input utterance
  @JsonProperty public final String utterance;

  // What we should try to predict.
  @JsonProperty public Value targetValue;  // Denotation (e.g., answer)

  //// Information after preprocessing (e.g., tokenization, POS tagging, NER, syntactic parsing, etc.).
  public LanguageInfo languageInfo = null;

  // Temporary state while parsing an Example (see Derivation.java for analogous struture).
  private Map<String, Object> tempState;

  // Statistics relating to processing the example.
  public Evaluation evaluation;

  //// Information for the learner
  public double weight = 1.0;

  public static class Builder {
    private String id;
    private String utterance;
    private Value targetValue;
    private LanguageInfo languageInfo;

    public Builder setId(String id) { this.id = id; return this; }
    public Builder setUtterance(String utterance) { this.utterance = utterance; return this; }
    public Builder setTargetValue(Value targetValue) { this.targetValue = targetValue; return this; }
    public Builder setLanguageInfo(LanguageInfo languageInfo) { this.languageInfo = languageInfo; return this; }
    public Builder withExample(Example ex) {
      setId(ex.id);
      setUtterance(ex.utterance);
      setTargetValue(ex.targetValue);
      return this;
    }
    public Example createExample() {
      return new Example(id, utterance, targetValue, languageInfo);
    }
  }

  @JsonCreator
  public Example(@JsonProperty("id") String id,
                 @JsonProperty("utterance") String utterance,
                 @JsonProperty("targetValue") Value targetValue,
                 @JsonProperty("languageInfo") LanguageInfo languageInfo) {
    this.id = id;
    this.utterance = utterance;
    this.targetValue = targetValue;
    this.languageInfo = languageInfo;
  }

  // Accessors
  public String getId() { return id; }
  public String getUtterance() { return utterance; }
  public int numTokens() { return languageInfo.tokens.size(); }

  public void setTargetValue(Value targetValue) { this.targetValue = targetValue; }

  public String spanString(int start, int end) {
    return String.format("%d:%d[%s]", start, end, start != -1 ? phraseString(start, end) : "...");
  }
  public String phraseString(int start, int end) {
    return Joiner.on(' ').join(languageInfo.tokens.subList(start, end));
  }

  // Return a string representing the tokens between start and end.
  public List<String> getTokens() { return languageInfo.tokens; }
  public List<String> getLemmaTokens() { return languageInfo.lemmaTokens; }
  public String token(int i) { return languageInfo.tokens.get(i); }
  public String lemmaToken(int i) { return languageInfo.lemmaTokens.get(i); }
  public String posTag(int i) { return languageInfo.posTags.get(i); }
  public String phrase(int start, int end) { return languageInfo.phrase(start, end); }
  public String lemmaPhrase(int start, int end) { return languageInfo.lemmaPhrase(start, end); }

  public String toJson() { return Json.writeValueAsStringHard(this); }
  public static Example fromJson(String json) { return Json.readValueHard(json, Example.class); }

  public void preprocess() {
    this.preprocess(LanguageAnalyzer.getSingleton());
  }

  public void preprocess(LanguageAnalyzer analyzer) {
    this.languageInfo = analyzer.analyze(this.utterance);
  }

  public void log() {
    LogInfo.begin_track("Example: %s", utterance);
    LogInfo.logs("Tokens: %s", getTokens());
    LogInfo.logs("Lemmatized tokens: %s", getLemmaTokens());
    LogInfo.logs("POS tags: %s", languageInfo.posTags);
    LogInfo.logs("NER tags: %s", languageInfo.nerTags);
    LogInfo.logs("NER values: %s", languageInfo.nerValues);
    if (targetValue != null)
      LogInfo.logs("targetValue: %s", targetValue);
    LogInfo.logs("Dependency children: %s", languageInfo.dependencyChildren);
    LogInfo.end_track();
  }

  public Map<String, Object> getTempState() {
    // Create the tempState if it doesn't exist.
    if (tempState == null)
      tempState = new HashMap<>();
    return tempState;
  }
  public void clearTempState() {
    tempState = null;
  }
}
