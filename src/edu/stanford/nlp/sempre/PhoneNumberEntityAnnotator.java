package edu.stanford.nlp.sempre;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

public class PhoneNumberEntityAnnotator implements Annotator {
  // recognize std syntax +... or north american 1-
  private static final Pattern INTL_PREFIX = Pattern.compile("^(\\+1-?|\\+[2-9][0-9]{1,2}-?|1-|1(?=\\())");
  // recognize common (000) area code, or just 0000, followed by optional -
  private static final Pattern AREA_CODE = Pattern.compile("^\\(?[0-9]{3,4}\\)?-?");
  // recognize numbers, *, # and -, or recognize full numbers in touch tone format
  private static final Pattern NUMBER = Pattern
      .compile("^([0-9*#\\-]+|[A-Za-z0-90-9*#\\-]{3,4}-[A-Za-z0-90-9*#\\-]{3,})$");
  // same thing, but more lenient with touch tones, if we have an explicit intl or area code prefix
  // parses things like 1-800-SABRINA, where the number part does not match NUMBER
  private static final Pattern LENIENT_NUMBER = Pattern
      .compile("^([A-Za-z0-90-9*#\\-]{3,})$");
  // but don't recognize something that would be a legitimate number
  private static final Pattern DOUBLE_PATTERN = Pattern.compile("[+\\-]?([0-9]*\\.[0-9]+|[0-9]+)([eE][+-]?[0-9]+)?");
  private static final Pattern DIGIT = Pattern.compile("[0-9]+");

  private static final char[] TOUCH_TONES = "22233344455566677778889999".toCharArray();

  private static class PhoneNumberParser {
    private final List<CoreLabel> tokens;
    private final int startToken;

    private int tokenIdx = 0;
    private int charIdx = 0;

    private boolean hasIntlPrefix = false;
    private boolean hasAreaCode = false;
    private final StringBuilder buffer = new StringBuilder();

    public PhoneNumberParser(List<CoreLabel> tokens, int startToken) {
      this.tokens = tokens;
      this.startToken = startToken;
    }

    private boolean tryIntlPrefix() {
      if (tokens.size() <= startToken + tokenIdx)
        return false;

      String token = tokens.get(startToken + tokenIdx).word();
      if (charIdx > 0)
        token = token.substring(charIdx);

      Matcher matcher = INTL_PREFIX.matcher(token);
      if (!matcher.find())
        return false;

      hasIntlPrefix = true;
      buffer.append(matcher.group());
      if (matcher.end() == token.length()) {
        tokenIdx++;
        charIdx = 0;
      } else {
        charIdx += matcher.end();
      }
      return true;
    }

    private boolean tryAreaCode() {
      if (tokens.size() <= startToken + tokenIdx)
        return false;

      String token = tokens.get(startToken + tokenIdx).word();
      if (charIdx > 0)
        token = token.substring(charIdx);

      Matcher matcher = AREA_CODE.matcher(token);
      if (!matcher.find())
        return false;

      hasAreaCode = true;
      buffer.append(matcher.group());
      if (matcher.end() == token.length()) {
        tokenIdx++;
        charIdx = 0;
      } else {
        charIdx += matcher.end();
      }
      return true;
    }

    private boolean tryNumber(boolean lenient) {
      if (tokens.size() <= startToken + tokenIdx)
        return false;

      String token = tokens.get(startToken + tokenIdx).word();
      if (charIdx > 0)
        token = token.substring(charIdx);
      if (charIdx == 0 && (hasAreaCode || hasIntlPrefix || buffer.length() >= 4))
        lenient = false;

      Matcher matcher = (lenient ? LENIENT_NUMBER : NUMBER).matcher(token);
      if (!matcher.matches())
        return false;

      buffer.append(matcher.group());
      tokenIdx++;
      charIdx = 0;
      return true;
    }

    public int consumedTokens() {
      return tokenIdx;
    }

    public String tryParse() {
      tryIntlPrefix();
      tryAreaCode();
      while (tryNumber(hasIntlPrefix || hasAreaCode))
        ;
      
      // reject anything with less than 6 chars, or with no tokens
      if (buffer.length() < 6 || tokenIdx == 0)
        return null;

      // if the buffer has 4 chars or less (plus 2 for intl prefix, plus 3 for area code), we don't accept it
      // if it parses as a double
      if (buffer.length() < (4 + (hasIntlPrefix ? 2 : 0) + (hasAreaCode ? 3 : 0))) {
        Matcher doubleMatcher = DOUBLE_PATTERN.matcher(buffer);
        if (doubleMatcher.matches())
          return null;
      }
      // if the buffer does not contain any digit, we reject it
      if (!DIGIT.matcher(buffer).find())
        return null;
      
      // normalize 1-... to +1-...
      if (buffer.substring(0, 1).equals("1"))
        buffer.insert(0, "+");
      else if (buffer.charAt(0) != '+')
        buffer.insert(0, "+1");

      // replace weird characters
      String str = buffer.toString();
      str = str.replace("-lrb-", "");
      str = str.replace("-rrb-", "");
      str = str.replaceAll("[()\\-]", "");
      str = str.toLowerCase();

      // replace touch tones
      buffer.setLength(0);
      for (int i = 0; i < str.length(); i++) {
        char c = str.charAt(i);
        if (c >= 'a' && c <= 'z')
          buffer.append(TOUCH_TONES[c - 'a']);
        else
          buffer.append(c);
      }
      
      return buffer.toString();
    }
  }

  @Override
  public void annotate(Annotation document) {
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (int i = 0; i < tokens.size(); i++) {
        CoreLabel token = tokens.get(i);
        String ner = token.ner();
        if (ner.equals("TIME") || ner.equals("DATE") || ner.equals("QUOTED_STRING"))
          continue;
        PhoneNumberParser parser = new PhoneNumberParser(tokens, i);
        String parsed = parser.tryParse();

        if (parsed != null) {
          for (int j = 0; j < parser.consumedTokens(); j++) {
            tokens.get(i + j).setNER("PHONE_NUMBER");
            tokens.get(i + j).set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, parsed);
          }
          i += parser.consumedTokens() - 1;
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
