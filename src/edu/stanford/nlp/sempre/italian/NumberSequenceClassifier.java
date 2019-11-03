package edu.stanford.nlp.sempre.italian;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PaddedList;

/**
 * A copy of edu.stanford.nlp.ie.regexp.NumberSequenceClassifier, with some
 * bugfixes and without sutime and a ton of other unneeded stuff.
 * 
 * Have been removed (compared to CoreNLP): - SUTime - Generic time words -
 * Handling of "m" and "b" to mean million and billion Have been added - Month
 * ORDINAL Have been fixed - months are case-insensitive
 * 
 * The rest of the CoreNLP doc-string follows:
 * 
 * A set of deterministic rules for marking certain entities, to add categories
 * and to correct for failures of statistical NER taggers. This is an extremely
 * simple and ungeneralized implementation of AbstractSequenceClassifier that
 * was written for PASCAL RTE. It could profitably be extended and generalized.
 * It marks a NUMBER category based on part-of-speech tags in a deterministic
 * manner. It marks an ORDINAL category based on word form in a deterministic
 * manner. It tags as MONEY currency signs and things tagged CD after a currency
 * sign. It marks a number before a month name as a DATE. It marks as a DATE a
 * word of the form xx/xx/xxxx (where x is a digit from a suitable range). It
 * marks as a TIME a word of the form x(x):xx (where x is a digit). It marks
 * everything else tagged "CD" as a NUMBER, and instances of "and" appearing
 * between CD tags in contexts suggestive of a number. It requires text to be
 * POS-tagged (have the getString(TagAnnotation.class) attribute). Effectively
 * these rules assume that this classifier will be used as a secondary
 * classifier by code such as ClassifierCombiner: it will mark most CD as
 * NUMBER, and it is assumed that something else with higher priority is marking
 * ones that are PERCENT, ADDRESS, etc.
 *
 * @author Christopher Manning
 * @author Mihai (integrated with NumberNormalizer, SUTime)
 */
class NumberSequenceClassifier extends AbstractSequenceClassifier<CoreLabel> {

  public NumberSequenceClassifier() {
    super(new Properties());
  }

  private static final boolean DEBUG = false;

  private static final Pattern MONTH_PATTERN = Pattern.compile(
      "gennaio|gen\\.?|febbraio|feb\\.?|marzo|mar\\.?|aprile|apr\\.?|maggio|mag\\.?|giugno|giu\\.?|luglio.|lug.\\.?|agosto|ago\\.?|settembre|sett?\\.?|ottobre|ott\\.?|novembre|nov\\.?|dicembre|dic\\.",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9]+");

  private static final Pattern YEAR_PATTERN = Pattern.compile("[1-3][0-9]{3}|'?[0-9]{2}");

  private static final Pattern DAY_PATTERN = Pattern.compile("(?:[1-9]|[12][0-9]|3[01])");

  private static final Pattern WEEKDAY_PATTERN = Pattern.compile(
      "lunedì|lun|martedì|mar|mercoledì|mer|giovedì|gio|venerdì|ven|sabato|sab|domenica|dom", Pattern.CASE_INSENSITIVE);

  private static final Pattern DATE_PATTERN = Pattern
      .compile("(?:[1-9]|[0-3][0-9])\\\\?/(?:[1-9]|[0-3][0-9])\\\\?/(?:[1-3][0-9]{3}|[0-9]{2})");

  private static final Pattern DATE_PATTERN2 = Pattern.compile("[12][0-9]{3}[-/](?:0?[1-9]|1[0-2])[-/][0-3][0-9]");

  private static final Pattern TIME_PATTERN = Pattern.compile("[0-2]?[0-9]:[0-5][0-9]");

  private static final Pattern TIME_PATTERN2 = Pattern.compile("[0-2][0-9]:[0-5][0-9]:[0-5][0-9]");

  public static final Pattern CURRENCY_WORD_PATTERN = Pattern.compile("dollar[oi]|cent|euro|penny|pence|yen|yuan|won",
      Pattern.CASE_INSENSITIVE);

  // pattern matches: dollar, pound sign XML escapes; pound sign, yen sign, euro,
  // won; other country dollars
  public static final Pattern CURRENCY_SYMBOL_PATTERN = Pattern
      .compile("\\$|&#163;|&pound;|\u00A3|\u00A5|\u20AC|\u20A9|(?:US|HK|A|C|NT|S|NZ)\\$", Pattern.CASE_INSENSITIVE);

  private static final String[] ALL_NUMBER_WORD_PIECES = { "uno", "due", "tre", "quattro", "cinque", "sei", "sette",
      "otto", "nove", "dieci", "undici", "dodici", "tredici", "quattordici", "quindici", "sedici", "diciassette",
      "diciotto", "diciannove", "venti", "ventuno", "trenta", "trentuno", "quaranta", "quarantuno", "cinquanta",
      "cinquantuno", "sessanta", "sessantuno", "settanta", "settantuno", "ottanta", "ottantuno", "novanta", "novantuno",
      "cento", "mille", "mila", "milione", "milioni", "miliardo", "miliardi" };
  // matches all numbers written as words, and a few non-sensical words
  // like "duedue" or "centotrecentoventimila" or "quarantatrenta"
  // we don't need to worry about non-sensical words because they won't appear in
  // the input
  public static final Pattern NUMBER_IN_WORDS_PATTERN = Pattern
      .compile("(?:" + Joiner.on('|').join(ALL_NUMBER_WORD_PIECES) + ")+");

  private static final Pattern ORDINAL_PATTERN = Pattern.compile(
      "(?:prim|second|terz|quart|quint|sest|settim|ottav|non|decim|undicesim|dodicesim|tredicesim|quattordicesim|quindicesim|sedicesim|diciassettesim|diciottesim|diciannovesim|[a-z]+(?:unesim|duesim|treesim|quattresim|cinquesim|seiesim|settesim|ottesim|novesim))[oaie]");

  private static final Pattern ORDINAL_SUFFIX_PATTERN = Pattern.compile("[ºª°]", Pattern.CASE_INSENSITIVE);

  private static final Pattern DAY_POINT_PATTERN = Pattern.compile("mezzogiorno|mezzodì|mezzanotte",
      Pattern.CASE_INSENSITIVE);
  
  private static final Pattern PART_OF_DAY_PATTERN = Pattern.compile("mattin[oa]|pomeriggio|sera|notte",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern OF_PATTERN = Pattern.compile("di|del|della", Pattern.CASE_INSENSITIVE);

  private static String ensure(String str) {
    if (str == null)
      return "";
    return str;
  }
  
  /**
   * Classify a {@link List} of {@link CoreLabel}s.
   *
   * @param document A {@link List} of {@link CoreLabel}s.
   * @return the same {@link List}, but with the elements annotated with their
   *         answers.
   */
  @Override
  public List<CoreLabel> classify(List<CoreLabel> document) {
    // if (DEBUG) { System.err.println("NumberSequenceClassifier tagging"); }

    for (int i = 0, sz = document.size(); i < sz; i++) {
      CoreLabel me = document.get(i);
      // a thing made of numbers is a number, let's not be stupid about it
      if (NUMBER_PATTERN.matcher(me.word()).matches()
          || (NUMBER_IN_WORDS_PATTERN.matcher(me.word()).matches() && !me.word().equals("uno")))
        me.setTag("N");
      
      if (i > 0) {
        CoreLabel prev = document.get(i-1);
        if (me.word().equals("uno") && prev.tag().equals("N"))
          me.setTag("N");
      }
    }

    PaddedList<CoreLabel> pl = new PaddedList<>(document, pad);
    for (int i = 0, sz = pl.size(); i < sz; i++) {
      CoreLabel me = pl.get(i);
      CoreLabel prev = pl.get(i - 1);
      CoreLabel next = pl.get(i + 1);
      CoreLabel next2 = pl.get(i + 2);

      if (me.get(CoreAnnotations.AnswerAnnotation.class) != null)
        continue;
      
      // if (DEBUG) { System.err.println("Tagging:" + me.word()); }
      me.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol);
      
      String myWord = me.word();
      String prevWord = ensure(prev.word());
      String nextWord = ensure(next.word());
      String next2Word = ensure(next2.word());
      
      String myTag = me.tag();
      String prevTag = ensure(prev.tag());
      String nextTag = ensure(next.tag());
      String next2Tag = ensure(next2.tag());

      if (CURRENCY_SYMBOL_PATTERN.matcher(myWord).matches() && ("N".equals(prevTag) || "N".equals(nextTag))) {
        // dollar, pound, pound, yen,
        // Penn Treebank ancient # as pound, euro,
        if (DEBUG) {
          System.err.println("Found currency sign:" + me.word());
        }
        me.set(CoreAnnotations.AnswerAnnotation.class, "MONEY");
      } else if (TIME_PATTERN.matcher(myWord).matches() || TIME_PATTERN2.matcher(myWord).matches()) {
        me.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
      } else if (DATE_PATTERN.matcher(myWord).matches() || DATE_PATTERN2.matcher(myWord).matches()) {
        me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
      } else if (myTag.equals("N")) { // numeral
        if (DEBUG) {
          System.err.println("Tagging N:" + me.word());
        }
        
        // number : number [: number]?
        // (they are tokenized into separate tokens, cause the tokenizer sucks...)
        if (NUMBER_PATTERN.matcher(myWord).matches() && ":".equals(nextWord) &&
            next2Tag.equals("N")) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
          next.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
          next2.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
          
          CoreLabel next3 = pl.get(i + 3);
          CoreLabel next4 = pl.get(i + 4);
          if (":".equals(next3.word()) &&
              "N".equals(next4.tag())) {
            next3.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
            next4.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
          }
          continue;
        }

        if (DAY_PATTERN.matcher(myWord).matches() && MONTH_PATTERN.matcher(nextWord).matches()) {
          // deterministically make DATE for British-style number before month
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
          if (WEEKDAY_PATTERN.matcher(prevWord).matches())
            prev.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        } else if (MONTH_PATTERN.matcher(nextWord).matches()
            || (OF_PATTERN.matcher(nextWord).matches() && MONTH_PATTERN.matcher(next2Word).matches())) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        } else if (OF_PATTERN.matcher(nextWord).matches() && PART_OF_DAY_PATTERN.matcher(next2Word).matches()) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
        } else if (YEAR_PATTERN.matcher(myWord).matches()
            && prev.getString(CoreAnnotations.AnswerAnnotation.class).equals("DATE")
            && (MONTH_PATTERN.matcher(prevWord).matches()
                || pl.get(i - 2).get(CoreAnnotations.AnswerAnnotation.class).equals("DATE"))) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        } else {
          if (DEBUG) {
            System.err.println("Found number:" + myWord);
          }
          if (prev.getString(CoreAnnotations.AnswerAnnotation.class).equals("MONEY")) {
            me.set(CoreAnnotations.AnswerAnnotation.class, "MONEY");
          } else {
            me.set(CoreAnnotations.AnswerAnnotation.class, "NUMBER");
          }
        }
      } else if (myTag.equals(",") && prev.getString(CoreAnnotations.AnswerAnnotation.class).equals("DATE")
          && YEAR_PATTERN.matcher(nextWord).matches()) {
        me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
      } else if (MONTH_PATTERN.matcher(myWord).matches()) {
        if (prev.getString(CoreAnnotations.AnswerAnnotation.class).equals("DATE") || nextTag.equals("N")
            || nextTag.equals("S") || nextTag.equals("A")) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");

          if (WEEKDAY_PATTERN.matcher(prevWord).matches())
            prev.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        }
      } else if (myTag.equals("CC")) { // coordinating conjunction
        if (prevTag.equals("N") && nextTag.equals("N")
            && myWord.equalsIgnoreCase("e")) {
          if (DEBUG) {
            System.err.println("Found number and:" + myWord);
          }
          String wd = prevWord;
          if (wd.equalsIgnoreCase("cento") || wd.equalsIgnoreCase("mille") || wd.equalsIgnoreCase("millione")
              || wd.equalsIgnoreCase("millioni") || wd.equalsIgnoreCase("miliardo")
              || wd.equalsIgnoreCase("miliardi")) {
            me.set(CoreAnnotations.AnswerAnnotation.class, "NUMBER");
          }
        }
      } else if (myTag.equals("S") || myTag.equals("SP") || myTag.equals("A")) {
        if (CURRENCY_WORD_PATTERN.matcher(me.word()).matches()) {
          if (prevTag.equals("N") && prev.getString(CoreAnnotations.AnswerAnnotation.class).equals("NUMBER")) {
            me.set(CoreAnnotations.AnswerAnnotation.class, "MONEY");

            for (int j = i - 1; j >= 0; j--) {
              CoreLabel prev2 = pl.get(j);
              if (ensure(prev2.tag()).equals("S") && prev2.getString(CoreAnnotations.AnswerAnnotation.class).equals("NUMBER"))
                prev2.set(CoreAnnotations.AnswerAnnotation.class, "MONEY");
              else
                break;
            }
          }
        }
        if (DAY_POINT_PATTERN.matcher(myWord).matches()) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "TIME");
        }
        if (ORDINAL_SUFFIX_PATTERN.matcher(myWord).matches()) {
          if ("NUMBER".equals(prev.get(CoreAnnotations.AnswerAnnotation.class))) {
            me.set(CoreAnnotations.AnswerAnnotation.class, "ORDINAL");
            prev.set(CoreAnnotations.AnswerAnnotation.class, "ORDINAL");
          } else if ("DATE".equals(prev.get(CoreAnnotations.AnswerAnnotation.class))) {
            me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
          }
        }
      } else if (myTag.equals("A")) { // adjective
        if (MONTH_PATTERN.matcher(nextWord).matches()
            || (OF_PATTERN.matcher(nextWord).matches() && MONTH_PATTERN.matcher(next2Word).matches())) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        } else if (MONTH_PATTERN.matcher(prevWord).matches()) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        } else if (ORDINAL_PATTERN.matcher(myWord).matches()) {
          // don't do other tags: don't want 'second' as noun, or 'first' as adverb
          // introducing reasons
          me.set(CoreAnnotations.AnswerAnnotation.class, "ORDINAL");
        }
      } else if (OF_PATTERN.matcher(myWord).matches()) {
        if (prevTag.equals("N") && MONTH_PATTERN.matcher(nextWord).matches()) {
          me.set(CoreAnnotations.AnswerAnnotation.class, "DATE");
        }
      }
    }
    return document;
  }

  // Implement other methods of AbstractSequenceClassifier interface

  @Override
  public void train(Collection<List<CoreLabel>> docs, DocumentReaderAndWriter<CoreLabel> readerAndWriter) {
  }

  @Override
  public void serializeClassifier(String serializePath) {
    System.err.print("Serializing classifier to " + serializePath + "...");
    System.err.println("done.");
  }

  @Override
  public void serializeClassifier(ObjectOutputStream oos) {
  }

  @Override
  public void loadClassifier(ObjectInputStream in, Properties props)
      throws IOException, ClassCastException, ClassNotFoundException {
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokenSequence, CoreMap document,
      CoreMap sentence) {
    return this.classify(tokenSequence);
  }

}