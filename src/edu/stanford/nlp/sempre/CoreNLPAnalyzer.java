package edu.stanford.nlp.sempre;

import java.io.*;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sempre.english.QuantifiableEntityNormalizer;
import edu.stanford.nlp.sempre.italian.ItalianQuantifiableEntityNormalizer;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

import opencc.OpenCC;

/**
 * CoreNLPAnalyzer uses Stanford CoreNLP pipeline to analyze an input string utterance
 * and return a LanguageInfo object
 *
 * @author akchou
 */
public class CoreNLPAnalyzer {
  private static final Redwood.RedwoodChannels log = Redwood.channels(CoreNLPAnalyzer.class);

  private static final String default_annotators = "tokenize,quote2,ssplit,pos,lemma," +
      "ner,quote_ner,custom_regexp_ner,custom_numeric_ner,phone_ner,url_ner,parse,sentiment";

  private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]+");
  private static final Pattern YEAR_PATTERN = Pattern.compile("[0-9]{4}");

  // recognize two numbers in one token, because CoreNLP's tokenizer will not split them
  private static final Pattern BETWEEN_PATTERN = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)-(-?[0-9]+(?:\\.[0-9]+)?)");

  // recognize a number followed by - and a word (as in "5-star hotel")
  private static final Pattern NUMBER_WORD_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)-([a-z]+)");

  private static final OpenCC openCC_t2s = new OpenCC("t2s");
  private static final OpenCC openCC_s2t = new OpenCC("s2t");

  private final StanfordCoreNLP pipeline;
  private final boolean isEnglish;
  private final boolean convertTraditionalChinese;

  public CoreNLPAnalyzer(LocaleTag localeTag) {
    Properties props = new Properties();
    String annotators = default_annotators;
    
    isEnglish = localeTag.getLanguage().equals("en");
    convertTraditionalChinese = "hant".equals(localeTag.getScript());

    switch (localeTag.getLanguage()) {
    case "en":
      props.put("pos.model", "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger");
      props.put("ner.model",
          "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz");

      // disable all the builtin numeric classifiers, we have our own
      props.put("ner.applyNumericClassifiers", "false");
      props.put("ner.useSUTime", "false");
      props.put("custom_regexp_ner.locale", "en");
      break;

    case "it":
      loadResource("StanfordCoreNLP-italian.properties", props);
      annotators = "ita_toksent,quote2,pos,ita_morpho,ita_lemma,ner,quote_ner,custom_regexp_ner,phone_ner,url_ner,parse,sentiment";
      props.put("custom_regexp_ner.locale", "it");
      break;

    case "de":
      loadResource("StanfordCoreNLP-german.properties", props);
      props.put("pos.model", "edu/stanford/nlp/models/pos-tagger/german/german-fast-caseless.tagger");
      break;

    case "fr":
      loadResource("StanfordCoreNLP-french.properties", props);
      break;

    case "zh":
      loadResource("StanfordCoreNLP-chinese.properties", props);
      break;

    case "es":
      loadResource("StanfordCoreNLP-spanish.properties", props);
      break;

    default:
      log.errf("Unrecognized language %s, analysis will not work!", localeTag.getLanguage());
    }

    props.put("annotators", annotators);

    // disable ssplit (even though we need it to run the rest of the annotators)
    props.put("ssplit.isOneSentence", "true");

    // enable regexner
    //props.put("regexner.mapping", "./data/regexner_gazette");
    props.put("regexner.ignorecase", "true");
    props.put("regexner.backgroundSymbol", "O,MISC,ORGANIZATION");

    // move quotes to a NER tag
    props.put("customAnnotatorClass.quote2", QuotedStringAnnotator.class.getCanonicalName());
    props.put("customAnnotatorClass.quote_ner", QuotedStringEntityAnnotator.class.getCanonicalName());
    props.put("customAnnotatorClass.phone_ner", PhoneNumberEntityAnnotator.class.getCanonicalName());
    props.put("customAnnotatorClass.url_ner", URLEntityAnnotator.class.getCanonicalName());
    props.put("customAnnotatorClass.custom_regexp_ner", RegexpEntityAnnotator.class.getCanonicalName());
    props.put("customAnnotatorClass.custom_numeric_ner", NumericEntityAnnotator.class.getCanonicalName());
    props.put("custom_regexp_ner.patterns", "./data/regex_patterns");

    // ask for binary tree parses
    props.put("parse.binaryTrees", "true");

    pipeline = new StanfordCoreNLP(props);
  }

  private static void loadResource(String name, Properties into) {
    try {
      InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
      into.load(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\p{IsWhite_Space}*");

  public LanguageInfo analyze(String utterance, String expected) {
    if (WHITE_SPACE_PATTERN.matcher(utterance).matches()) {
        // CoreNLP chokes on sentences that are composed exclusively of blanks
        // return early in that case, the tokenization has 0 tokens
      return new LanguageInfo("neutral");
    }

    // Fix wrong tokenization of "<number>gb" without a space
    if (isEnglish)
      utterance = utterance.replaceAll("([0-9])(?!am|pm)([a-zA-Z])", "$1 $2");

    // Convert Traditional Chinese to Simplified Chinese
    if (convertTraditionalChinese) {
      String raw_utterance = utterance;
      utterance = openCC_t2s.convert(utterance);
    }

    // Run Stanford CoreNLP

    // Work around CoreNLP issue #622
    Annotation annotation = pipeline.process(utterance + " ");

    CoreMap sentence = annotation.get(SentencesAnnotation.class).get(0);
    String sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);

    if (sentiment == null)
      sentiment = "neutral";
    else
      sentiment = sentiment.replaceAll("\\s+", "_").toLowerCase();

    LanguageInfo languageInfo = new LanguageInfo(sentiment);

    for (CoreLabel token : annotation.get(TokensAnnotation.class)) {
      String word = token.get(TextAnnotation.class);
      String wordLower = word.toLowerCase();
      String nerTag = token.get(NamedEntityTagAnnotation.class);
      if (nerTag == null)
        nerTag = "O";
      String nerValue = token.get(NormalizedNamedEntityTagAnnotation.class);
      String posTag = token.get(PartOfSpeechAnnotation.class);

      boolean addComma = false;
      if (word.endsWith(",") && !",".equals(word)) {
        word = word.substring(0, word.length() - 1);
        wordLower = wordLower.substring(0, wordLower.length() - 1);
        addComma = true;
      }

      if (nerTag.equals("DATE")) {
        if (nerValue == null) {
          nerTag = "O";
        } else if (YEAR_PATTERN.matcher(nerValue).matches()) {
          nerTag = "NUMBER";
        }
      }

      if (wordLower.equals("9-11") || wordLower.equals("911") || wordLower.equals("110") || wordLower.equals("119") ||
          (INTEGER_PATTERN.matcher(wordLower).matches() && wordLower.length() == 5)
          && !"QUOTED_STRING".equals(nerTag)) {
        nerTag = "O";
      } else {
        Matcher twoNumbers = BETWEEN_PATTERN.matcher(wordLower);
        if (twoNumbers.matches()) {
          // CoreNLP does something somewhat dumb when it comes to X-Y when X and Y are both numbers
          // we want to split them and treat them separately
          String num1 = twoNumbers.group(1);
          String num2 = twoNumbers.group(2);

          languageInfo.tokens.add(num1);
          languageInfo.lemmaTokens.add(num1);
          languageInfo.posTags.add("CD");
          languageInfo.nerTags.add("NUMBER");
          languageInfo.nerValues.add(num1);

          languageInfo.tokens.add("-");
          languageInfo.lemmaTokens.add("-");
          languageInfo.posTags.add(":");
          languageInfo.nerTags.add("O");
          languageInfo.nerValues.add(null);

          languageInfo.tokens.add(num2);
          languageInfo.lemmaTokens.add(num2);
          languageInfo.posTags.add("CD");
          languageInfo.nerTags.add("NUMBER");
          languageInfo.nerValues.add(num2);
          continue;
        }

        Matcher numberWord = NUMBER_WORD_PATTERN.matcher(wordLower);
        if (numberWord.matches()) {
          // split 5-star into "5 star"
          String num = numberWord.group(1);
          String newWord = numberWord.group(2);

          languageInfo.tokens.add(num);
          languageInfo.lemmaTokens.add(num);
          languageInfo.posTags.add("CD");
          languageInfo.nerTags.add("NUMBER");
          languageInfo.nerValues.add(num);

          languageInfo.tokens.add(newWord);
          languageInfo.lemmaTokens.add(token.get(LemmaAnnotation.class));
          languageInfo.posTags.add(token.get(PartOfSpeechAnnotation.class));
          languageInfo.nerTags.add("O");
          languageInfo.nerValues.add(null);
          continue;
        }
      }

      languageInfo.tokens.add(wordLower);
      languageInfo.posTags.add(token.get(PartOfSpeechAnnotation.class));
      languageInfo.lemmaTokens.add(token.get(LemmaAnnotation.class));

      // if it's not a noun and not an adjective it's not an organization 
      if (!posTag.startsWith("N") && !posTag.startsWith("J") && nerTag.equals("ORGANIZATION"))
        nerTag = "O";

      languageInfo.nerTags.add(nerTag);
      languageInfo.nerValues.add(nerValue);

      if (addComma) {
        languageInfo.tokens.add(",");
        languageInfo.posTags.add(",");
        languageInfo.lemmaTokens.add(",");
        languageInfo.nerTags.add("O");
        languageInfo.nerValues.add(null);
      }
    }

    // fix corenlp's tokenizer being weird around "/"
    boolean inquote = false;
    int n = languageInfo.tokens.size();
    for (int i = 0; i < n; i++) {
      String token = languageInfo.tokens.get(i);
      if ("``".equals(token)) {
        inquote = true;
        continue;
      }
      if ("''".equals(token)) {
        if (inquote) {
          inquote = false;
          continue;
        }
        if (i < n - 2 && languageInfo.tokens.get(i + 1).equals("/") && languageInfo.tokens.get(i + 2).equals("''")) {
          languageInfo.tokens.set(i, "``");
          inquote = true;
        }
      }
    }

    if ("MultipleChoice".equals(expected)) {
      // remove all entity tags if we expect a multiple choice
      // this will allow the NN parser to pick the closest choice, by passing the semantic
      // parser
      for (int i = 0; i < n; i++) {
        languageInfo.nerTags.set(i, "O");
        languageInfo.nerValues.set(i, null);
      }
    }

    // Convert Simplified Chinese back to Traditional Chinese if needed
    if (convertTraditionalChinese) {
      for (int i = 0; i < languageInfo.tokens.size(); i++)
        languageInfo.tokens.set(i, openCC_s2t.convert(languageInfo.tokens.get(i)));
      for (int i = 0; i < languageInfo.lemmaTokens.size(); i++)
        languageInfo.lemmaTokens.set(i, openCC_s2t.convert(languageInfo.lemmaTokens.get(i)));
    }

    return languageInfo;
  }

  // Test on example sentence.
  public static void main(String[] args) {
    CoreNLPAnalyzer analyzer = new CoreNLPAnalyzer(new LocaleTag(args.length > 0 ? args[0] : "en"));

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      while (true) {
        System.out.println("Enter some text:");
        String text = reader.readLine();
        if (text == null)
          break;
        LanguageInfo langInfo = analyzer.analyze(text, null);
        Redwood.startTrack();
        log.logf("Analyzing \"%s\"", text);
        log.logf("tokens: %s", langInfo.tokens);
        log.logf("lemmaTokens: %s", langInfo.lemmaTokens);
        log.logf("posTags: %s", langInfo.posTags);
        log.logf("nerTags: %s", langInfo.nerTags);
        log.logf("nerValues: %s", langInfo.nerValues);
        log.logf("sentiment: %s", langInfo.sentiment);
        Redwood.endTrack();
      }
    } catch (IOException e) {
        e.printStackTrace();
    }
  }
}
