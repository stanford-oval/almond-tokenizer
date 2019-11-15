package edu.stanford.nlp.sempre.italian;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class ItalianTokenizerBlankWorkaround implements Annotator {

  @Override
  public void annotate(Annotation document) {
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      boolean prevIsUnderscore = false;
      CoreLabel prevword = null;
      
      Iterator<CoreLabel> it = sentence.get(CoreAnnotations.TokensAnnotation.class).iterator();
      while (it.hasNext()) {
        CoreLabel token = it.next();
        boolean isUnderscore = "_".equals(token.word());
        
        if (isUnderscore) {
          if (prevIsUnderscore) {
            assert prevword != null;
            prevword.setWord(prevword.word() + "_");
            prevword.setValue(prevword.value() + "_");
            prevword.set(CoreAnnotations.OriginalTextAnnotation.class, token.get(CoreAnnotations.OriginalTextAnnotation.class) + "_");
            prevword.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
            it.remove();
          } else {
            prevIsUnderscore = true;
            prevword = token;
          }
        } else {
          prevIsUnderscore = false;
          prevword = null;
        }
      }
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
        CoreAnnotations.SentencesAnnotation.class)));
  }

}
