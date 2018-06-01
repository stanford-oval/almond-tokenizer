package edu.stanford.nlp.sempre;

import java.io.*;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * CoreNLPAnalyzer uses Stanford CoreNLP pipeline to analyze an input string utterance
 * and return a LanguageInfo object
 *
 * @author akchou
 */
public class CoreNLPAnalyzer {
  private static final Redwood.RedwoodChannels log = Redwood.channels(CoreNLPAnalyzer.class);

  // Observe that we run almost everything twice
  // This is because NER and quote_ner have to run before spellcheck (so that spellcheck
  // doesn't try to fix names and quotes), but we want to rerun lemmatization
  // after spellcheck so that new spaces and slash-splitting that spellcheck does
  // are reflected in the lemma tokens and POS tags
  private static final String annotators = "tokenize,quote2,ssplit,pos,lemma," +
      "ner,quote_ner,custom_regexp_ner,phone_ner,url_ner";

  private static final Pattern INTEGER_PATTERN = Pattern.compile("[0-9]{4}");

  private final StanfordCoreNLP pipeline;

  public CoreNLPAnalyzer(String languageTag) {
    Properties props = new Properties();
    
    switch (languageTag) {
    case "en":
    case "en_US":
      props.put("pos.model", "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger");
      props.put("ner.model",
          "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz");
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
      log.errf("Unrecognized language %s, analysis will not work!", languageTag);
    }

    props.put("annotators", annotators);

    // disable ssplit (even though we need it to run the rest of the annotators)
    props.put("ssplit.isOneSentence", "true");

    // disable all the builtin numeric classifiers, we have our own
    props.put("ner.applyNumericClassifiers", "false");
    props.put("ner.useSUTime", "false");

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
    props.put("custom_regexp_ner.patterns", "./data/regex_patterns");

    // enable spell checking with our custom annotator
    props.put("customAnnotatorClass.spellcheck", SpellCheckerAnnotator.class.getCanonicalName());
    props.put("spellcheck.dictPath", languageTag);

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

  // recognize two numbers in one token, because CoreNLP's tokenizer will not split them
  private static final Pattern BETWEEN_PATTERN = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)-(-?[0-9]+(?:\\.[0-9]+)?)");

  private void recognizeNumberSequences(List<CoreLabel> words) {
    QuantifiableEntityNormalizer.applySpecializedNER(words);
  }

  private static final Pattern WHITE_SPACE_PATTERN = Pattern.compile("\\p{IsWhite_Space}*");

  public LanguageInfo analyze(String utterance, String expected) {
    LanguageInfo languageInfo = new LanguageInfo();

    languageInfo.tokens.clear();
    languageInfo.posTags.clear();
    languageInfo.nerTags.clear();
    languageInfo.nerValues.clear();
    languageInfo.lemmaTokens.clear();

    if (WHITE_SPACE_PATTERN.matcher(utterance).matches()) {
        // CoreNLP chokes on sentences that are composed exclusively of blanks
        // return early in that case, the tokenization has 0 tokens
        return languageInfo;
    }

    utterance = utterance.replaceAll("([0-9])(?!am|pm)([a-zA-Z])", "$1 $2");

    // Run Stanford CoreNLP

    // Work around CoreNLP issue #622
    Annotation annotation = pipeline.process(utterance + " ");

    // run numeric classifiers
    recognizeNumberSequences(annotation.get(CoreAnnotations.TokensAnnotation.class));

    for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
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

      if (nerTag.equals("DATE") && INTEGER_PATTERN.matcher(nerValue).matches()) {
        nerTag = "NUMBER";
      }

      if (wordLower.equals("9-11") || wordLower.equals("911")) {
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

    // fix corenlp sometimes tagging Washington as location in "washington post"
    for (int i = 0; i < n - 1; i++) {
      String token = languageInfo.tokens.get(i);
      String next = languageInfo.tokens.get(i + 1);

      if (!("O".equals(languageInfo.nerTags.get(i)) ||
          "ORGANIZATION".equals(languageInfo.nerTags.get(i)) ||
          "LOCATION".equals(languageInfo.nerTags.get(i))))
        continue;

      if ("washington".equals(token) && ("post".equals(next) || "posts".equals(next))) {
        languageInfo.nerTags.set(i, "ORGANIZATION");
        languageInfo.nerValues.set(i, null);

        languageInfo.nerTags.set(i + 1, "ORGANIZATION");
        languageInfo.nerValues.set(i + 1, null);
      }

      if ("us".equals(token) && "business".equals(next)) {
        languageInfo.nerTags.set(i, "O");
        languageInfo.nerValues.set(i, null);
      }

      // apple post is not a f... newspaper
      // stupid corenlp
      if ("apple".equals(token) && "post".equals(next)) {
        languageInfo.nerTags.set(i + 1, "O");
        languageInfo.nerValues.set(i + 1, null);
      }

      // topic is not an organization
      if ("topic".equals(token)) {
        languageInfo.nerTags.set(i, "O");
        languageInfo.nerValues.set(i, null);
      }

      // merge locations separated by a comma (eg Palo Alto, California)
      if (",".equals(token) && i >0 && "LOCATION".equals(languageInfo.nerTags.get(i-1))
          && "LOCATION".equals(languageInfo.nerTags.get(i+1))) {
        languageInfo.nerTags.set(i, "LOCATION");
      }
    }

    if ("Location".equals(expected)) {
      // override all entity tags if we expect a location
      // that will just throw everything at MapQuest
      for (int i = 0; i < n; i++) {
        languageInfo.nerTags.set(i, "LOCATION");
        languageInfo.nerValues.set(i, null);
      }
    } else if ("MultipleChoice".equals(expected)) {
      // remove all entity tags if we expect a multiple choice
      // this will allow the NN parser to pick the closest choice, by passing the semantic
      // parser
      for (int i = 0; i < n; i++) {
        languageInfo.nerTags.set(i, "O");
        languageInfo.nerValues.set(i, null);
      }
    }

    return languageInfo;
  }

  // Test on example sentence.
  public static void main(String[] args) {
    CoreNLPAnalyzer analyzer = new CoreNLPAnalyzer("en");

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
        Redwood.endTrack();
      }
    } catch (IOException e) {
        e.printStackTrace();
    }
  }
}
