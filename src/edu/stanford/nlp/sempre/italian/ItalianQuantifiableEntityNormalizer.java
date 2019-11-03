package edu.stanford.nlp.sempre.italian;

import static java.lang.System.err;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sempre.AbstractQuantifiableEntityNormalizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.EditDistance;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * A copy of edu.stanford.nlp.ie.regexp.QuantifiableEntityNormalizer, with
 * some bugfixes and without sutime and a ton of other unneeded stuff.
 * 
 * Have been removed:
 * - SUTime
 * - ranges
 * - millions abbreviated as "m" or "m."
 * - less than / greater than
 * 
 * The rest of the CoreNLP doc-string follows:
 * 
 * Various methods for normalizing Money, Date, Percent, Time, and
 * Number, Ordinal amounts.
 * These matchers are generous in that they try to quantify something
 * that's already been labelled by an NER system; don't use them to make
 * classification decisions. This class has a twin in the pipeline world:
 * {@link edu.stanford.nlp.pipeline.QuantifiableEntityNormalizingAnnotator}.
 * Please keep the substantive content here, however, so as to lessen code
 * duplication.
 * <p>
 * <i>Implementation note:</i> The extensive test code for this class is
 * now in a separate JUnit Test class. This class depends on the background
 * symbol for NER being the default background symbol. This should be fixed
 * at some point.
 *
 * @author Chris Cox
 * @author Christopher Manning (extended for RTE)
 * @author Anna Rafferty
 */
public class ItalianQuantifiableEntityNormalizer implements AbstractQuantifiableEntityNormalizer {

  private static final boolean DEBUG = false;
  private static final boolean DEBUG2 = false;  // String normlz functions

  public static final String BACKGROUND_SYMBOL = "O";

  private static final Pattern timePattern = Pattern
      .compile("([0-2]?[0-9])((?::?[0-5][0-9]){0,2})");

  private static final Pattern moneyPattern = Pattern
      .compile("([$\u00A3\u00A5\u20AC#]?)(-?[0-9,]+(?:\\.[0-9]*)?|\\.[0-9]+)[-a-zA-Z]*");
  
  // note: the pattern assumes the input is already Americanized
  private static final Pattern decimalPattern = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?");

  //Collections of entity types
  private static final Set<String> quantifiable;  //Entity types that are quantifiable
  private static final Map<String, String> timeUnitWords;
  private static final Map<String, Double> moneyMultipliers;
  private static final Map<String, Character> currencyWords;
  public static final ClassicCounter<String> wordsToValues;
  public static final ClassicCounter<String> ordinalsToValues;

  static {

    quantifiable = Generics.newHashSet();
    quantifiable.add("MONEY");
    quantifiable.add("TIME");
    quantifiable.add("DATE");
    quantifiable.add("PERCENT");
    quantifiable.add("NUMBER");
    quantifiable.add("ORDINAL");
    quantifiable.add("DURATION");

    timeUnitWords = Generics.newHashMap();
    timeUnitWords.put("secondo", "S");
    timeUnitWords.put("secondi", "S");
    timeUnitWords.put("minuto", "m");
    timeUnitWords.put("minuti", "m");
    timeUnitWords.put("ora", "H");
    timeUnitWords.put("ore", "H");
    timeUnitWords.put("giorno", "D");
    timeUnitWords.put("giorni", "D");
    timeUnitWords.put("settimana", "W");
    timeUnitWords.put("settimane", "W");
    timeUnitWords.put("mese", "M");
    timeUnitWords.put("mesi", "M");
    timeUnitWords.put("anno", "Y");
    timeUnitWords.put("anni", "Y");

    currencyWords = Generics.newHashMap();
    currencyWords.put("dollar[oi]", '$');
    currencyWords.put("cent", '$');
    currencyWords.put("sterlin[ae]", '\u00A3');
    currencyWords.put("penny", '\u00A3');
    currencyWords.put("yen", '\u00A5');
    // "euri" is ungrammatical as a plural of "euro" but i've seen it in the wild
    currencyWords.put("eur[oi]", '\u20AC');
    
    // an equivalent translation of "bucks" would be "sacchi"
    // but it's regional and very colloquial so I'm not including it here
    
    currencyWords.put("won", '\u20A9');
    currencyWords.put("\\$", '$');
    currencyWords.put("\u00A2", '$');  // cents
    currencyWords.put("\u00A3", '\u00A3');  // pounds
    currencyWords.put("#", '\u00A3');      // for Penn treebank
    currencyWords.put("\u00A5", '\u00A5');  // Yen
    currencyWords.put("\u20AC", '\u20AC');  // Euro
    currencyWords.put("\u20A9", '\u20A9');  // Won
    currencyWords.put("yuan", '\u5143');   // Yuan

    moneyMultipliers = Generics.newHashMap();
    moneyMultipliers.put("^miliard[io]", 1000000000.0);
    moneyMultipliers.put("^milion[ie]", 1000000.0);
    moneyMultipliers.put("^mila", 1000.0);

    wordsToValues = new ClassicCounter<>();
    wordsToValues.setCount("zero", 0.0);
    wordsToValues.setCount("uno", 1.0);
    wordsToValues.setCount("due", 2.0);
    wordsToValues.setCount("tre", 3.0);
    wordsToValues.setCount("quattro", 4.0);
    wordsToValues.setCount("cinque", 5.0);
    wordsToValues.setCount("sei", 6.0);
    wordsToValues.setCount("sette", 7.0);
    wordsToValues.setCount("otto", 8.0);
    wordsToValues.setCount("nove", 9.0);
    wordsToValues.setCount("dieci", 10.0);
    wordsToValues.setCount("undici", 11.0);
    wordsToValues.setCount("dodici", 12.0);
    wordsToValues.setCount("tredici", 13.0);
    wordsToValues.setCount("quattordici", 14.0);
    wordsToValues.setCount("quindici", 15.0);
    wordsToValues.setCount("sedici", 16.0);
    wordsToValues.setCount("diciassette", 17.0);
    wordsToValues.setCount("diciotto", 18.0);
    wordsToValues.setCount("diciannove", 19.0);
    wordsToValues.setCount("ventuno", 21.0);
    wordsToValues.setCount("trentuno", 31.0);
    wordsToValues.setCount("quarantuno", 41.0);
    wordsToValues.setCount("cinquantuno", 51.0);
    wordsToValues.setCount("sessantuno", 61.0);
    wordsToValues.setCount("settantuno", 71.0);
    wordsToValues.setCount("ottantuno", 81.0);
    wordsToValues.setCount("novantuno", 91.0);
    wordsToValues.setCount("venti", 20.0);
    wordsToValues.setCount("trenta", 30.0);
    wordsToValues.setCount("quaranta", 40.0);
    wordsToValues.setCount("cinquanta", 50.0);
    wordsToValues.setCount("sessanta", 60.0);
    wordsToValues.setCount("settanta", 70.0);
    wordsToValues.setCount("ottanta", 80.0);
    wordsToValues.setCount("novanta", 90.0);
    wordsToValues.setCount("mille", 1000.0);

    ordinalsToValues = new ClassicCounter<>();
    ordinalsToValues.setCount("prim", 1.0);
    ordinalsToValues.setCount("second", 2.0);
    ordinalsToValues.setCount("terz", 3.0);
    ordinalsToValues.setCount("quart", 4.0);
    ordinalsToValues.setCount("quint", 5.0);
    ordinalsToValues.setCount("sest", 6.0);
    ordinalsToValues.setCount("settim", 7.0);
    ordinalsToValues.setCount("ottav", 8.0);
    ordinalsToValues.setCount("non", 9.0);
    ordinalsToValues.setCount("decim", 10.0);
    ordinalsToValues.setCount("undicesim", 11.0);
    ordinalsToValues.setCount("dodicesim", 12.0);
    ordinalsToValues.setCount("tredicesim", 13.0);
    ordinalsToValues.setCount("quattordicesim", 14.0);
    ordinalsToValues.setCount("quindicesim", 15.0);
    ordinalsToValues.setCount("sedicesim", 16.0);
    ordinalsToValues.setCount("diciasettesim", 17.0);
    ordinalsToValues.setCount("diciottesim", 18.0);
    ordinalsToValues.setCount("diciannovesim", 19.0);
    ordinalsToValues.setCount("ventesim", 20.0);
    ordinalsToValues.setCount("ventunesim", 21.0);
    ordinalsToValues.setCount("ventiduesim", 22.0);
    ordinalsToValues.setCount("ventitreesim", 23.0);
    ordinalsToValues.setCount("ventiquattresim", 24.0);
    ordinalsToValues.setCount("venticinquesim", 25.0);
    ordinalsToValues.setCount("ventiseiesim", 26.0);
    ordinalsToValues.setCount("ventisettesim", 27.0);
    ordinalsToValues.setCount("ventottesim", 28.0);
    ordinalsToValues.setCount("ventinovesim", 29.0);
    ordinalsToValues.setCount("trentesim", 30.0);
    ordinalsToValues.setCount("trentunesim", 31.0);
    ordinalsToValues.setCount("quarantesim", 40.0);
    ordinalsToValues.setCount("cinquantesim", 50.0);
    ordinalsToValues.setCount("sessantesim", 60.0);
    ordinalsToValues.setCount("settantesim", 70.0);
    ordinalsToValues.setCount("ottanetesim", 80.0);
    ordinalsToValues.setCount("novantesim", 90.0);
    ordinalsToValues.setCount("centesim", 100.0);
    ordinalsToValues.setCount("millesim", 1000.0);
    ordinalsToValues.setCount("milionesim", 1000000.0);
    ordinalsToValues.setCount("miliardesim", 1000000000.0);
  }

  public ItalianQuantifiableEntityNormalizer() {
  }

  /**
   * This method returns the closest match in set such that the match
   * has more than three letters and differs from word only by one substitution,
   * deletion, or insertion. If not match exists, returns null.
   */
  private static String getOneSubstitutionMatch(String word, Set<String> set) {
    // TODO (?) pass the EditDistance around more places to make this
    // more efficient.  May not really matter.
    EditDistance ed = new EditDistance();
    for (String cur : set) {
      if (isOneSubstitutionMatch(word, cur, ed))
        return cur;
    }
    return null;
  }

  private static boolean isOneSubstitutionMatch(String word, String match,
      EditDistance ed) {
    if (word.equalsIgnoreCase(match))
      return true;
    if (match.length() > 3) {
      if (ed.score(word, match) <= 1)
        return true;
    }
    return false;
  }

  /**
   * Convert the content of a List of CoreMaps to a single
   * space-separated String. This grabs stuff based on the
   * get(CoreAnnotations.NamedEntityTagAnnotation.class) field.
   * [CDM: Changed to look at NamedEntityTagAnnotation not AnswerClass Jun 2010,
   * hoping that will fix a bug.]
   *
   * @param l
   *          The List
   * @return one string containing all words in the list, whitespace separated
   */
  private static <E extends CoreMap> String singleEntityToString(List<E> l) {
    String entityType = l.get(0).get(CoreAnnotations.NamedEntityTagAnnotation.class);
    StringBuilder sb = new StringBuilder();
    for (E w : l) {
      assert (w.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(entityType));
      sb.append(w.get(CoreAnnotations.TextAnnotation.class));
      sb.append(' ');
    }
    return sb.toString();
  }

  /**
   * Returns a string that represents either a single date or a range of
   * dates. Representation pattern is roughly ISO8601, with some extensions
   * for greater expressivity; see {@link ISODateInstance} for details.
   * 
   * @param s
   *          Date string to normalize
   * @param openRangeMarker
   *          a marker for whether this date is not involved in
   *          an open range, is involved in an open range that goes forever
   *          backward and
   *          stops at s, or is involved in an open range that goes forever
   *          forward and
   *          starts at s. See {@link ISODateInstance}.
   * @return A yyyymmdd format normalized date
   */
  private static String normalizedDateString(String s) {
    if (s.endsWith(" , "))
      s = s.substring(0, s.length() - 3);

    ISODateInstance d = new ISODateInstance(s);
    if (DEBUG2)
      err.println("normalizeDate: " + s + " to " + d.getDateString());
    return (d.getDateString());
  }

  private static String normalizedDurationString(String s) {
    s = s.trim();
    int space = s.lastIndexOf(' ');
    if (space < 0)
      return null;
    String timeword = s.substring(space+1);
    String numword = s.substring(0, space);
    
    String multiplier = timeUnitWords.get(timeword);
    if (multiplier == null)
      return null;
    
    String number = normalizedNumberString(numword);
    return "P" + number + multiplier;
  }

  private static String normalizedTimeString(String s) {
    return normalizedTimeString(s, null);
  }

  private static String normalizedTimeString(String s, String ampm) {
    if (DEBUG2)
      err.println("normalizingTime: " + s);
    s = s.replaceAll("[ \t\n\0\f\r]", "");
    Matcher m = timePattern.matcher(s);
    if (s.equalsIgnoreCase("mezzogiorno")) {
      return "T12:00";
    } else if (s.equalsIgnoreCase("mezzanotte")) {
      return "T00:00";
    } else if (m.matches()) {
      if (DEBUG2) {
        err.printf("timePattern matched groups: |%s| |%s| |%s| |%s|\n", m.group(0), m.group(1), m.group(2), m.group(3));
      }
      // group 1 is hours, group 2 is minutes and maybe seconds
      StringBuilder sb = new StringBuilder();
      int hour = Integer.valueOf(m.group(1));
      if (ampm != null) {
        if (ampm.equals("pm") && hour < 12)
          hour += 12;
        else if (ampm.equals("am") && hour == 12)
          hour = 0;
      }
      sb.append("T" + (hour < 10 ? "0" : "") + Integer.toString(hour));
      if (m.group(2) == null || "".equals(m.group(2))) {
        sb.append(":00");
      } else {
        sb.append(m.group(2));
      }
      if (DEBUG2) {
        err.println("normalizedTimeString new str: " + sb.toString());
      }
      return sb.toString();
    } else if (DEBUG) {
      err.println("Quantifiable: couldn't normalize " + s);
    }
    return null;
  }

  /**
   * Convert Italian number format (123.456,78) into International (123456.78).
   *
   */
  private static String convertToAmerican(String s) {
    // drop all periods
    s = s.replace(".", "");
    
    //turn all but the last into blanks - this isn't really correct, but it's close enough for now
    while (s.indexOf(',') != s.lastIndexOf(','))
      s = s.replaceFirst(",", "");
     
    int place = s.lastIndexOf(',');
    if (place < 0)
      return s;
    s = s.substring(0, place) + '.' + s.substring(place + 1);
    return s;
  }

  private static String normalizedMoneyString(String s) {
    s = convertToAmerican(s);
    // clean up string
    s = s.replaceAll("[ \t\n\0\f\r,]", " ");
    s = s.toLowerCase();
    if (DEBUG2) {
      err.println("normalizedMoneyString: Normalizing " + s);
    }

    double multiplier = 1.0;

    // do currency words
    char currencySign = '€';
    for (String currencyWord : currencyWords.keySet()) {
      if (StringUtils.find(s, currencyWord)) {
        if (DEBUG2) {
          err.println("Found units: " + currencyWord);
        }
        if (currencyWord.equals("penny") || currencyWord.equals("cent") || currencyWord.equals("\u00A2")) {
          multiplier *= 0.01;
        }
        // if(DEBUG){err.println("Quantifiable: Found "+ currencyWord);}
        s = s.replaceAll(currencyWord, "");
        currencySign = currencyWords.get(currencyWord);
      }
    }

    // process rest as number
    String value = normalizedNumberStringQuiet(s, multiplier);
    if (value == null) {
      return null;
    } else {
      return currencySign + value;
    }
  }

  private static String normalizedNumberString(String s) {
    if (DEBUG2) {
      err.println("normalizedNumberString: normalizing " + s);
    }
    return normalizedNumberStringQuiet(s, 1.0);
  }

  private static final Pattern allSpaces = Pattern.compile(" *");

  private static String normalizedNumberStringQuiet(String s,
      double multiplier) {

    // clean up string
    s = s.replaceAll("[ \t\n\0\f\r]+", " ");
    if (allSpaces.matcher(s).matches()) {
      return null;
    }
    //see if it looks like european style
    s = convertToAmerican(s);
    // remove parenthesis around numbers
    // if PTBTokenized, this next bit should be a no-op
    // in some contexts parentheses might indicate a negative number, but ignore that.
    if (s.startsWith("(") && s.endsWith(")")) {
      s = s.substring(1, s.length() - 1);
      if (DEBUG2)
        err.println("Deleted (): " + s);
    }
    s = s.toLowerCase();
    
    // handle numbers written in words
    
    // concatenate everything together
    s = s.replaceAll("[ -]", "");
    if (DEBUG2)
      err.println("Looking for number words in |" + s + "|; multiplier is " + multiplier);
    
    double value = 0;
    double currentTerm = 0;
    
    boolean first = true;
    while (s.length() > 0) {
      if (s.startsWith("cento")) {
        if (currentTerm == 0.0)
          currentTerm = 100;
        else
          currentTerm *= 100;
        s = s.substring("cento".length());
        first = false;
        continue;
      }
      
      boolean found = false;
      if (!first) {
        for (String mult : moneyMultipliers.keySet()) {
          Matcher matcher = Pattern.compile(mult).matcher(s);
          
          if (matcher.find()) {
            if (currentTerm == 0.0)
              currentTerm = moneyMultipliers.get(mult);
            else
              currentTerm *= moneyMultipliers.get(mult);
            found = true;
            s = s.substring(matcher.end());
            value += currentTerm;
            currentTerm = 0;
            break;
          }
        }
        if (found)
          continue;
      }
      
      Matcher decimalMatcher = decimalPattern.matcher(s);
      if (decimalMatcher.find()) {
        currentTerm += Double.parseDouble(decimalMatcher.group());
        s = s.substring(decimalMatcher.end());
        first = false;
        continue;
      }
      
      for (String word : wordsToValues.keySet()) {
        if (s.startsWith(word)) {
          currentTerm += wordsToValues.getCount(word);
          found = true;
          s = s.substring(word.length());
          break;
        }
      }
      
      if (!found)
        break;

      first = false;
    }
    
    value += currentTerm;
    return Double.toString(value);
  }

  private static String normalizedOrdinalString(String s) {
    if (DEBUG2) {
      err.println("normalizedOrdinalString: normalizing " + s);
    }
    return normalizedOrdinalStringQuiet(s);
  }

  private static final Pattern numberPattern = Pattern.compile("([0-9.]+)");

  private static String normalizedOrdinalStringQuiet(String s) {
    // clean up string
    s = s.replaceAll("[ \t\n\0\f\r,]", "");
    // remove parenthesis around numbers
    // if PTBTokenized, this next bit should be a no-op
    // in some contexts parentheses might indicate a negative number, but ignore that.
    if (s.startsWith("(") && s.endsWith(")")) {
      s = s.substring(1, s.length() - 1);
      if (DEBUG2)
        err.println("Deleted (): " + s);
    }
    s = s.toLowerCase();

    if (DEBUG2)
      err.println("Looking for ordinal words in |" + s + '|');
    if (Character.isDigit(s.charAt(0))) {
      Matcher matcher = numberPattern.matcher(s);
      matcher.find();
      // just parse number part, assuming last two letters are st/nd/rd
      return normalizedNumberStringQuiet(matcher.group(), 1.0);
    } else if (ordinalsToValues.containsKey(s.substring(0, s.length()-1))) {
      return Double.toString(ordinalsToValues.getCount(s.substring(0, s.length()-1)));
    } else {
      String val = getOneSubstitutionMatch(s, ordinalsToValues.keySet());
      if (val != null)
        return Double.toString(ordinalsToValues.getCount(val));
      else
        return null;
    }
  }

  private static String normalizedPercentString(String s) {
    if (DEBUG2) {
      err.println("normalizedPercentString: " + s);
    }
    s = s.replaceAll("\\s", "");
    s = s.toLowerCase();
    if (s.contains("%") || s.contains("percent")) {
      s = s.replaceAll("percent|%", "");
    }
    String norm = normalizedNumberStringQuiet(s, 1.0);
    if (norm == null) {
      return null;
    }
    return '%' + norm;
  }

  private static <E extends CoreMap> List<E> processEntity(List<E> l,
      String entityType, String compModifier) {
    assert (quantifiable.contains(entityType));
    if (DEBUG) {
      System.err.println("Quantifiable.processEntity: " + l);
    }
    String s = singleEntityToString(l);

    if (DEBUG)
      System.err.println("Quantifiable: working on " + s);
    String p = null;
    switch (entityType) {
    case "NUMBER": {
      p = "";
      if (compModifier != null) {
        p = compModifier;
      }
      String q = normalizedNumberString(s);
      if (q != null) {
        p = p.concat(q);
      } else {
        p = null;
      }
      break;
    }
    case "ORDINAL":
      p = normalizedOrdinalString(s);
      break;
    case "DURATION":
      p = normalizedDurationString(s);
      break;
    case "MONEY": {
      p = "";
      if (compModifier != null) {
        p = compModifier;
      }
      String q = normalizedMoneyString(s);
      if (q != null) {
        p = p.concat(q);
      } else {
        p = null;
      }
      break;
    }
    case "DATE":
      p = normalizedDateString(s);
      break;
    case "TIME": {
      p = "";
      assert compModifier == null || compModifier.equals("") || compModifier.matches("am|pm");
      String q = normalizedTimeString(s, compModifier != null ? compModifier : "");
      if (q != null && q.length() == 1 && !q.equals("D")) {
        p = p.concat(q);
      } else {
        p = q;
      }
      break;
    }
    case "PERCENT": {
      p = "";
      if (compModifier != null) {
        p = compModifier;
      }
      String q = normalizedPercentString(s);
      if (q != null) {
        p = p.concat(q);
      } else {
        p = null;
      }
      break;
    }
    }
    if (DEBUG) {
      err.println("Quantifiable: Processed '" + s + "' as '" + p + '\'');
    }

    int i = 0;
    for (E wi : l) {
      if (p != null) {
        if (DEBUG) {
          System.err.println("#4: Changing normalized NER from "
              + wi.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + p + " at index " + i);
        }
        wi.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, p);
      }
      //currently we also write this into the answers;
      //wi.setAnswer(wi.get(CoreAnnotations.AnswerAnnotation.class)+"("+p+")");
      i++;
    }
    return l;
  }

  /**
   * @param l
   *          The list of tokens in a time entity
   * @return the word in the time word list that should be normalized
   */
  private static <E extends CoreMap> String timeEntityToString(List<E> l) {
    String entityType = l.get(0).get(CoreAnnotations.AnswerAnnotation.class);
    int size = l.size();
    for (E w : l) {
      assert (w.get(CoreAnnotations.AnswerAnnotation.class) == null ||
          w.get(CoreAnnotations.AnswerAnnotation.class).equals(entityType));
      Matcher m = timePattern.matcher(w.get(CoreAnnotations.TextAnnotation.class));
      if (m.matches())
        return w.get(CoreAnnotations.TextAnnotation.class);
    }
    if (DEBUG) {
      System.err.println("default: " + l.get(size - 1).get(CoreAnnotations.TextAnnotation.class));
    }
    return l.get(size - 1).get(CoreAnnotations.TextAnnotation.class);
  }

  /**
   * Takes the output of an {@link AbstractSequenceClassifier} and marks up
   * each document by normalizing quantities. Each {@link CoreLabel} in any
   * of the documents which is normalizable will receive a "normalizedQuantity"
   * attribute.
   *
   * @param l
   *          a {@link List} of {@link List}s of {@link CoreLabel}s
   * @return The list with normalized entity fields filled in
   */
  private static List<List<CoreLabel>> normalizeClassifierOutput(List<List<CoreLabel>> l) {
    for (List<CoreLabel> doc : l) {
      addNormalizedQuantitiesToEntities(doc);
    }
    return l;
  }

  // al mattino, alla mattina, della mattina, etc.
  private static String amTwoWords = "((al|del)(la)?|di) (mattin[oa]|notte)";
  private static String pmTwoWords = "(della|alla|di) sera|(del|al|di) pomeriggio";

  /**
   * Takes the strings of the three previous words to a quantity and detects a
   * quantity modifier like "less than", "more than", etc.
   * Any of these words may be <code>null</code> or an empty String.
   */
  private static <E extends CoreMap> String detectTimeOfDayModifier(List<E> list, int beforeIndex, int afterIndex) {
    String prev = (beforeIndex >= 0) ? list.get(beforeIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase()
        : "";
    String prev2 = (beforeIndex - 1 >= 0)
        ? list.get(beforeIndex - 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String prev3 = (beforeIndex - 2 >= 0)
        ? list.get(beforeIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    int sz = list.size();
    String next = (afterIndex < sz) ? list.get(afterIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String next2 = (afterIndex + 1 < sz)
        ? list.get(afterIndex + 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String next3 = (afterIndex + 2 < sz)
        ? list.get(afterIndex + 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";

    String longPrev = prev3 + ' ' + prev2 + ' ' + prev;

    longPrev = prev2 + ' ' + prev;
    if (longPrev.matches("mattin[oa] (all'?|alle|alla)")) {
      return "am";
    } else if (longPrev.matches("(sera|pomeriggio) (all'?|alle|alla)")) {
      return "pm";
    }

    String longNext = next + ' ' + next2;
    if (longNext.matches(amTwoWords)) {
      return "am";
    }
    
    if (longNext.matches(pmTwoWords)) {
      return "pm";
    }
    
    return "";
  }

  /**
   * Identifies contiguous MONEY, TIME, DATE, or PERCENT entities
   * and tags each of their constituents with a "normalizedQuantity"
   * label which contains the appropriate normalized string corresponding to
   * the full quantity.
   *
   * @param list
   *          A list of {@link CoreMap}s representing a single
   *          document. Note: the Labels are updated in place.
   * @param concatenate
   *          true if quantities should be concatenated into one label, false
   *          otherwise
   */
  private static <E extends CoreLabel> void addNormalizedQuantitiesToEntities(List<E> list) {
    // Goes through tokens and tries to fix up NER annotations
    fixupNerBeforeNormalization(list);

    // Now that NER tags has been fixed up, we do another pass to add the normalization
    String prevNerTag = BACKGROUND_SYMBOL;
    String timeModifier = "";
    ArrayList<E> collector = new ArrayList<>();
    for (int i = 0, sz = list.size(); i <= sz; i++) {
      E wi = null;
      String currNerTag = null;
      if (i < list.size()) {
        wi = list.get(i);
        if (DEBUG) {
          System.err.println("addNormalizedQuantitiesToEntities: wi is " + wi + "; collector is " + collector);
        }

        currNerTag = wi.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        if ("TIME".equals(currNerTag)) {
          if (timeModifier.equals("")) {
            timeModifier = detectTimeOfDayModifier(list, i - 1, i + 1);
          }
        }
      }

      E wprev = (i > 0) ? list.get(i - 1) : null;
      // if the current wi is a non-continuation and the last one was a
      // quantity, we close and process the last segment.
      if ((currNerTag == null || !currNerTag.equals(prevNerTag))
          && quantifiable.contains(prevNerTag)) {
        // special handling of TIME
        switch (prevNerTag) {
        case "TIME":
          processEntity(collector, prevNerTag, timeModifier);
          timeModifier = "";
          break;
        case "DATE":
          processEntity(collector, prevNerTag, null);
          break;
        default:
          processEntity(collector, prevNerTag, null);
          break;
        }

        collector = new ArrayList<>();
      }

      // if the current wi is a quantity, we add it to the collector.
      // if its the first word in a quantity, we record index before it
      if (quantifiable.contains(currNerTag)) {
        collector.add(wi);
      }
      prevNerTag = currNerTag;
    }

    // finally, we do one last pass to join times and dates together
    for (int i = 1, sz = list.size(); i < sz; i++) {
      E wi = list.get(i);
      String currNerTag = wi.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      E wprev = list.get(i-1);
      prevNerTag = wprev.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      String currNerValue = wi.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
      String prevNerValue = wprev.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
      
      if ("TIME".equals(currNerTag) && currNerValue != null && currNerValue.charAt(0) == 'T'
          && "DATE".equals(prevNerTag)) {
        if (prevNerValue == null || prevNerValue.indexOf('T') < 0) {
          for (int j = i - 1; j >= 0; j--) {
            E wj = list.get(j);
            if (!"DATE".equals(wj.get(CoreAnnotations.NamedEntityTagAnnotation.class)) ||
                !prevNerValue.equals(wj.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)))
              break;
            wj.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, prevNerValue + currNerValue);
          }
          prevNerValue = prevNerValue + currNerValue;
        }
        wi.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
        wi.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, prevNerValue);
      }
    }
  }

  private static <E extends CoreLabel> void fixupNerBeforeNormalization(List<E> list) {
    // Goes through tokens and tries to fix up NER annotations
    String prevNerTag = BACKGROUND_SYMBOL;
    for (int i = 0, sz = list.size(); i < sz; i++) {
      E wi = list.get(i);

      String curWord = (wi.get(CoreAnnotations.TextAnnotation.class) != null
          ? wi.get(CoreAnnotations.TextAnnotation.class) : "");
      String currNerTag = wi.get(CoreAnnotations.NamedEntityTagAnnotation.class);

      if (DEBUG) {
        System.err.println("fixupNerBeforeNormalization: wi is " + wi);
      }

      // repairs comma, "at" between DATE and TIME
      if ((i + 1) < sz &&
          (",".equals(curWord) || curWord.matches("al|alle"))
          && "DATE".equals(prevNerTag)
          && "TIME".equals(list.get(i + 1).ner())) {
        wi.setNER("DATE");
      }
      
      // "DATE alle ore TIME" -> "DATE"
      // (lit. "DATE at time TIME")
      if ((i + 2) < sz &&
          (curWord.matches("alle") && list.get(i+1).word().equals("ore"))
          && "DATE".equals(prevNerTag)
          && "TIME".equals(list.get(i + 2).ner())) {
        wi.setNER("DATE");
        list.get(i+1).setNER("DATE");
      }

      //repairs mistagged multipliers after a numeric quantity
      if (!curWord.equals("") && (moneyMultipliers.containsKey(curWord) ||
          (getOneSubstitutionMatch(curWord, moneyMultipliers.keySet()) != null)) &&
          prevNerTag != null && (prevNerTag.equals("MONEY") || prevNerTag.equals("NUMBER"))) {
        wi.setNER(prevNerTag);
      }

      // Marks time units as DURATION if they are preceded by a NUMBER tag.  e.g. "two years" or "5 minutes"
      if (timeUnitWords.containsKey(curWord) &&
          (currNerTag == null || !"DURATION".equals(currNerTag)) &&
          ("NUMBER".equals(prevNerTag))) {
        wi.setNER("DURATION");
        for (int j = i - 1; j >= 0; j--) {
          E prev = list.get(j);
          if ("NUMBER".equals(prev.get(CoreAnnotations.NamedEntityTagAnnotation.class))) {
            prev.setNER("DURATION");
          } else {
            break;
          }
        }
      }

      prevNerTag = currNerTag;
    }
  }

  /**
   * Runs a deterministic named entity classifier which is good at recognizing
   * numbers and money and date expressions not recognized by our statistical
   * NER. It then changes any BACKGROUND_SYMBOL's from the list to
   * the value tagged by this deterministic NER.
   * It then adds normalized values for quantifiable entities.
   *
   * @param l
   *          A document to label
   * @return The list with results of 'specialized' (rule-governed) NER filled
   *         in
   */
  @Override
  public <E extends CoreLabel> List<E> applySpecializedNER(List<E> l) {
    int sz = l.size();
    // copy l
    List<CoreLabel> copyL = new ArrayList<>(sz);
    for (int i = 0; i < sz; i++) {
      if (DEBUG2) {
        if (i == 1) {
          String tag = l.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class);
          if (tag == null || tag.equals("")) {
            err.println("Quantifiable: error! tag is " + tag);
          }
        }
      }
      copyL.add(new CoreLabel(l.get(i)));
    }
    // run NumberSequenceClassifier
    AbstractSequenceClassifier<CoreLabel> nsc = new NumberSequenceClassifier();
    copyL = nsc.classify(copyL);
    // update entity only if it was not O
    for (int i = 0; i < sz; i++) {
      E before = l.get(i);
      CoreLabel nscAnswer = copyL.get(i);

      // copy over any POS tag override that NumberSequenceClassifier applied
      before.setTag(nscAnswer.tag());

      // copy over the NER tag too
      if ((before.get(CoreAnnotations.NamedEntityTagAnnotation.class) == null
          || before.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(BACKGROUND_SYMBOL)
          || before.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals("MISC")) &&
          (nscAnswer.get(CoreAnnotations.AnswerAnnotation.class) != null
              && !nscAnswer.get(CoreAnnotations.AnswerAnnotation.class).equals(BACKGROUND_SYMBOL))) {
        before.set(CoreAnnotations.NamedEntityTagAnnotation.class,
            nscAnswer.get(CoreAnnotations.AnswerAnnotation.class));
      }
    }

    addNormalizedQuantitiesToEntities(l);
    return l;
  } // end applySpecializedNER

}