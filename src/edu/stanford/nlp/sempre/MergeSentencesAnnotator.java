package edu.stanford.nlp.sempre;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

/**
 * Undo sentence-splitting.
 * 
 * Normally, sentence splitting is disabled using ssplit.isOneSentence,
 * but the Italian tokenizer doesn't recognize that.
 * 
 * @author gcampagn
 *
 */
public class MergeSentencesAnnotator implements Annotator {
  @Override
  public void annotate(Annotation document) {
    String originalText = document.get(CoreAnnotations.TextAnnotation.class);
    Annotation newSentence = new Annotation(originalText);
    
    newSentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, 0);
    newSentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, originalText.length());
    newSentence.set(CoreAnnotations.SentenceIndexAnnotation.class, 0);
    
    List<CoreLabel> tokens = new ArrayList<>();
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class))
      tokens.addAll(sentence.get(CoreAnnotations.TokensAnnotation.class));
   
    newSentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    document.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(newSentence));
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
        CoreAnnotations.SentencesAnnotation.class)));
  }
}
