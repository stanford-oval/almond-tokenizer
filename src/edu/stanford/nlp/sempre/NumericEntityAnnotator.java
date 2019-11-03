package edu.stanford.nlp.sempre;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.sempre.english.EnglishQuantifiableEntityNormalizer;
import edu.stanford.nlp.sempre.italian.ItalianQuantifiableEntityNormalizer;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class NumericEntityAnnotator implements Annotator {
  private static final Map<String, Class<? extends AbstractQuantifiableEntityNormalizer>> normalizerClasses = new HashMap<>();
  static {
    normalizerClasses.put("en", EnglishQuantifiableEntityNormalizer.class);
    normalizerClasses.put("it", ItalianQuantifiableEntityNormalizer.class);
  }
  
  private final AbstractQuantifiableEntityNormalizer normalizer;
  
  public NumericEntityAnnotator(Properties properties) {
    String locale = properties.getProperty("custom_numeric_annotator.language");
    
    if (locale == null || !normalizerClasses.containsKey(locale)) {
      normalizer = null;
    } else {
      try {
        normalizer = normalizerClasses.get("locale").newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void annotate(Annotation document) {
    if (normalizer == null)
      return;
    
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      normalizer.applySpecializedNER(tokens);
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
