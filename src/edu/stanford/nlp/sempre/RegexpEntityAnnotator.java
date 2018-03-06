package edu.stanford.nlp.sempre;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class RegexpEntityAnnotator implements Annotator {
  private final Map<String, Pattern> patterns = new HashMap<>();

  public RegexpEntityAnnotator(String name, Properties props) {
    this(props.getProperty(name + ".patterns"));
  }

  public RegexpEntityAnnotator(String file) {
    for (String line : IOUtils.readLines(file)) {
      String[] parts = line.trim().split("\t");
      patterns.put(parts[0], Pattern.compile(parts[1]));
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        String existingNerTag = token.ner();
        if ("QUOTED_STRING".equals(existingNerTag))
          continue;
        
        for (Map.Entry<String,Pattern> entry : patterns.entrySet()) {
          Matcher matcher = entry.getValue().matcher(token.word());
          if (matcher.matches()) {
            token.setNER(entry.getKey());
            token.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, matcher.group(1));
          }
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
