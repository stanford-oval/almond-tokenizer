package edu.stanford.nlp.sempre;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class NumericEntityAnnotator implements Annotator {
  private static void recognizeNumberSequences(List<CoreLabel> words) {
    QuantifiableEntityNormalizer.applySpecializedNER(words);
  }

  @Override
  public void annotate(Annotation document) {
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      recognizeNumberSequences(tokens);
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.PositionAnnotation.class,
        CoreAnnotations.NamedEntityTagAnnotation.class)));
  }
}
