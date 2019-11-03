package edu.stanford.nlp.sempre.italian;

import edu.stanford.nlp.ie.QuantifiableEntityNormalizer;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents dates and times according to ISO8601 standard while also allowing for
 * wild cards - e.g., can represent "21 June" without a year.
 * (Standard ISO8601 only allows removing less precise annotations (e.g.,
 * 200706 rather than 20070621 but not a way to represent 0621 without a year.)
 *
 * Format stores date and time separately since the majority of current use
 * cases involve only one of these items.  Standard ISO 8601 instead
 * requires &lt;date&gt;T&lt;time&gt;.
 *
 * Ranges are specified within the strings via forward slash.  For example
 * 6 June - 8 June is represented ****0606/****0608.  6 June onward is
 * ****0606/ and until 8 June is /****0608.
 * 
 * Modified to parse Italian dates instead of English dates.
 *
 * @author Anna Rafferty
 *         TODO: add time support - currently just dates are supported
 */
class ISODateInstance  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ISODateInstance.class);

  private static final boolean DEBUG = false;
  private final ArrayList<String> tokens = new ArrayList<>(); //each token contains some piece of the date, from our input.
  
  private static final Pattern[] monthArray = {
      Pattern.compile("gennaio|gen\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("febbraio|feb\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("marzo|mar\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("aprile|apr\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("maggio|mag\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("giugno|giu\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("luglio|lug\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("agosto|ago?\\.", Pattern.CASE_INSENSITIVE),
      Pattern.compile("settembre|sett?\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("ottobre|ott\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("novembre|nov\\.?", Pattern.CASE_INSENSITIVE),
      Pattern.compile("dicembre|dic\\.?", Pattern.CASE_INSENSITIVE)
  };
  
  private static final Pattern[] weekdayArray = {
      Pattern.compile("domenica", Pattern.CASE_INSENSITIVE),
      Pattern.compile("lunedì", Pattern.CASE_INSENSITIVE),
      Pattern.compile("martedì", Pattern.CASE_INSENSITIVE),
      Pattern.compile("mercoledì", Pattern.CASE_INSENSITIVE),
      Pattern.compile("giovedì", Pattern.CASE_INSENSITIVE),
      Pattern.compile("venerdì", Pattern.CASE_INSENSITIVE),
      Pattern.compile("sabato", Pattern.CASE_INSENSITIVE)
  };

  /**
   * String of the format {@literal <year><month><day>}.  Representations
   * by week are also allowed. If a more general field (such as year)
   * is not specified when a less general one (such as month) is, the characters
   * normally filled by the more general field are replaced by asterisks. For example,
   * 21 June would be \"****0621\".  Less general fields are simply truncated;
   * for example, June 2007 would be \"200706\".
   */
  private String isoDate = "";

  //Variable for marking if we were unable to parse the string associated with this isoDate
  private boolean unparseable = false;

  //private String isoTime = "";

  /**
   * Takes a string that represents a date, and attempts to
   * normalize it into ISO 8601-compatible format.
   */
  public ISODateInstance(String date) {
    extractFields(date);
  }

  public String toString() {
    return isoDate;
  }

  /**
   * Provided for backwards compatibility with DateInstance;
   * returns the same thing as toString()
   *
   */
  public String getDateString() {
    return this.toString();
  }

  /**
   * Uses regexp matching to match  month, day, and year fields.
   * TODO: Find a way to mark what's already been handled in the string
   */
  private boolean extractFields(String inputDate) {
    inputDate = inputDate.trim();
    
    // remove junk that QuantifiableEntityNormalizer appended
    inputDate = inputDate.replaceAll("\\s+(al|alle|alle ore)\\s*$", "");
    
    tokenizeDate(inputDate);
    
    if (DEBUG) {
      log.info("Extracting date: " + inputDate);
    }

    if (extractYYYYMMDD(inputDate)) {
      return true;
    }
    if (extractDDMMYY(inputDate)) {
      return true;
    }
    boolean passed = false;
    passed = extractYear(inputDate) || passed;
    passed = extractMonth(inputDate) || passed;
    passed = extractDay(inputDate) || passed;

    if (!passed) {//couldn't parse
      //try one more trick
      unparseable = true;
      boolean weekday = extractWeekday(inputDate);
      if (!weekday) {
        isoDate = inputDate;
      }
    }
    return passed;
  }

  /**
   * Returns true iff we were unable to parse the input
   * String associated with this date; in that case,
   * we just store the input string and shortcircuit
   * all of the comparison methods
   *
   */
  public boolean isUnparseable() {
    return unparseable;
  }

  /* -------------------------- Tokenization and Field Extraction -------------------------- */
  //These methods are taken directly from or modified slightly from {@link DateInstance}

  private void tokenizeDate(String inputDate) {
    inputDate = inputDate.replace("-", " - ");
    inputDate = inputDate.replace(",", " ");
    
    tokens.addAll(Arrays.asList(inputDate.split("\\s+")));
    if(DEBUG) {
      System.out.println("tokens:" + tokens);
    }
  }


  /**
   * This method does YYYY-MM-DD style ISO date formats
   *
   * @return whether it worked.
   */
  private boolean extractYYYYMMDD(String inputDate) {
    Pattern pat = Pattern.compile("([12][0-9]{3})[ /-]?([01]?[0-9])[ /-]([0-3]?[0-9])");
    Matcher m = pat.matcher(inputDate);
    if (m.matches()) {
      if (DEBUG) {
        log.info("YYYYMMDD succeeded");
      }
      String monthValue = m.group(2);
      if (monthValue.length() < 2)//we always use two digit months
      {
        monthValue = '0' + monthValue;
      }
      String dayValue = m.group(3);
      if (dayValue.length() < 2) {
        dayValue = '0' + dayValue;
      }
      String yearString = m.group(1);
      isoDate = yearString + monthValue + dayValue;
      return true;
    }
    return false;
  }

  private boolean extractDDMMYY(String inputDate) {
    Pattern pat = Pattern.compile("([0-3]??[0-9])[ \\t\\r\\n\\f]*[/-][ \\t\\r\\n\\f]*([0-1]??[0-9])[ \t\n\r\f]*[/-][ \t\n\r\f]*([0-2]??[0-9]??[0-9][0-9])[ \t\r\n\f]*");
    Matcher m = pat.matcher(inputDate);
    if (m.matches()) {
      if (DEBUG) {
        log.info("MMDDYY succeeded");
      }
      String dayValue = m.group(1);
      if (dayValue.length() < 2) {
        dayValue = '0' + dayValue;
      }
      String monthValue = m.group(2);
      if (monthValue.length() < 2)//we always use two digit months
      {
        monthValue = '0' + monthValue;
      }
      
      String yearString; // always initialized below
      if (m.group(3).length() == 2) {
        int yearInt = Integer.parseInt(m.group(3));
        //Now we add "20" or "19" to the front of the two digit year depending on its value....
        if (yearInt < 50) {
          yearString = "20" + m.group(3);
        } else {
          yearString = "19" + m.group(3);
        }

      } else {
        yearString = m.group(3);
      }
      //lastYearSet = new Integer(yearString).intValue();
      isoDate = yearString + monthValue + dayValue;
      return true;
    }
    return false;
  }

  private Pattern re1 = Pattern.compile("[1-2][0-9]{3}|'[0-9]{2}");
  private Pattern re2 = Pattern.compile("[0-9][^0-9].*([0-9]{2})\\s*$");

  private boolean extractYear(String inputDate) {
    if (DEBUG) {
      log.info("Extracting year from: |" + inputDate + '|');
    }
    String extract;
    Matcher m1 = re1.matcher(inputDate);
    Matcher m2 = re2.matcher(inputDate);
    if (m1.find()) {
      extract = m1.group(0);
    } else if (m2.find()) {
      extract = m2.group(1);
    } else {
      isoDate = "****";
      return false;
    }

    if ( ! "".equals(extract)) {
      if (extract.charAt(0) == '\'') {
        extract = extract.substring(1);
      }
      extract = extract.trim();
      if (extract.length() == 2) {
        if (extract.charAt(0) < '5') {
          extract = "20" + extract;
        } else {
          extract = "19" + extract;
        }
      }
      if (inputDate.charAt(inputDate.length() - 1) == 's') {//decade or century marker
        if (extract.charAt(2) == '0') {//e.g., 1900s -> 1900/1999
          String endDate = Integer.toString((Integer.parseInt(extract) + 99));
          extract = extract + '/' + endDate;
        } else {//e.g., 1920s -> 1920/1929
          String endDate = Integer.toString((Integer.parseInt(extract) + 9));
          extract = extract + '/' + endDate;
        }
      }
      isoDate = extract;
      if (DEBUG) {
        log.info("year extracted:" + extract);
      }
      return true;
    }
    isoDate = "****";
    return false;
  }

  private boolean extractMonth(String inputDate) {
    boolean foundMonth = false;

    for (int i = 0; i < 12; i++) {
      String extract = "";
      Matcher m = monthArray[i].matcher(inputDate);
      if (m.find()) {
        extract = m.group(0);
      }
      if ( ! "".equals(extract)) {
        if (!foundMonth) {
          if (DEBUG) {
            log.info("month extracted: " + extract);
          }
          int monthNum = i + 1;
          if (isoDate.length() != 4) {
            isoDate = "****";
          }
          String month = (monthNum < 10) ? "0" + monthNum : String.valueOf(monthNum);
          isoDate += month;
          foundMonth = true;
        }
      }
    }
    return foundMonth;
  }

  private boolean extractDay(String inputDate) {
    try {
      for (String extract : tokens) {
        if (QuantifiableEntityNormalizer.wordsToValues.containsKey(extract)) {
          extract = Integer.toString(Double.valueOf(QuantifiableEntityNormalizer.wordsToValues.getCount(extract)).intValue());
        } else if (QuantifiableEntityNormalizer.ordinalsToValues.containsKey(extract)) {
          extract = Integer.toString(Double.valueOf(QuantifiableEntityNormalizer.ordinalsToValues.getCount(extract)).intValue());
        }
        extract = extract.replaceAll("[^0-9]", "");
        if ( ! extract.isEmpty()) {
          Long i = Long.parseLong(extract);
          if (i.intValue() < 32L && i.intValue() > 0L) {
            if (isoDate.length() < 6) { //should already have year and month
              if (isoDate.length() != 4) { //throw new RuntimeException("Error extracting dates; should have had month and year but didn't");
                isoDate = isoDate + "******";
              } else {
                isoDate = isoDate + "**";
              }
            }
            String day = (i < 10) ? "0" + i : String.valueOf(i);
            isoDate = isoDate + day;
            return true;
          }
        }
      }
    } catch (NumberFormatException e) {
      log.info("Exception in extract Day.");
      log.info("tokens size :" + tokens.size());
      e.printStackTrace();
    }
    return false;
  }

  /**
   * This is a backup method if everything else fails.  It searches for named
   * days of the week and if it finds one, it sets that as the date in lowercase form
   *
   */
  private boolean extractWeekday(String inputDate) {
    for (Pattern p : weekdayArray) {
      Matcher m = p.matcher(inputDate);
      if (m.find()) {
        String extract = m.group(0);
        isoDate = extract.toLowerCase();
        return true;
      }
    }
    return false;
  }
}
