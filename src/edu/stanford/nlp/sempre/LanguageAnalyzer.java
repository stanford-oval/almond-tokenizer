package edu.stanford.nlp.sempre;

import fig.basic.Option;
import fig.basic.Utils;

/**
 * LanguageAnalyzer takes an utterance and applies various NLP pre-processing steps to
 * to output a LanguageInfo object
 *
 * @author Alex Ratner
 */
public abstract class LanguageAnalyzer {
  public static class Options {
    @Option
    public String languageAnalyzer = "corenlp.CoreNLPAnalyzer";
  }
  public static Options opts = new Options();

  // We keep a singleton LanguageAnalyzer because for any given run we
  // generally will be working with one.
  private static LanguageAnalyzer singleton;
  public static LanguageAnalyzer getSingleton() {
    if (singleton == null)
      singleton = (LanguageAnalyzer) Utils.newInstanceHard(SempreUtils.resolveClassName(opts.languageAnalyzer));
    return singleton;
  }

  public abstract LanguageInfo analyze(String utterance);
}
