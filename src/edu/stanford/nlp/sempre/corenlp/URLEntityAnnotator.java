package edu.stanford.nlp.sempre.corenlp;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class URLEntityAnnotator implements Annotator {
  private static final Pattern REGEXP = Pattern
      .compile("(https?://(?:www\\.|(?!www))[^\\.]+\\..{2,}|www\\..+\\..{2,}|.{2,}\\.(?:com|net|org))");

  @Override
  public void annotate(Annotation document) {
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        String existingNerTag = token.ner();
        if ("QUOTED_STRING".equals(existingNerTag) || "EMAIL_ADDRESS".equals(existingNerTag))
          continue;
        Matcher matcher = REGEXP.matcher(token.word());
        if (matcher.matches()) {
          String url = matcher.group();
          if (!url.startsWith("http"))
            url = "http://" + url;

          token.setNER("URL");
          token.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, url);
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
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.PositionAnnotation.class,
        CoreAnnotations.NamedEntityTagAnnotation.class)));
  }
}
