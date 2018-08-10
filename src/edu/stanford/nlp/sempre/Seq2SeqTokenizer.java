package edu.stanford.nlp.sempre;

import java.util.*;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ParserAnnotator;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

public class Seq2SeqTokenizer {
  private static final boolean INCLUDE_CONSTITUENCY_PARSE = false;

  public static class Result {
    public final List<String> rawTokens = new ArrayList<>();
    public final List<String> tokens = new ArrayList<>();
    public final List<String> tokensNoQuotes = new ArrayList<>();
    public final List<String> posTags = new ArrayList<>();
    public final Map<Value, List<Integer>> entities = new HashMap<>();
    public final List<String> constituencyParse = new ArrayList<>();
  }

  private static final Map<String, String> CURRENCY_CODES = new HashMap<>();

  static {
    CURRENCY_CODES.put("$", "usd");
    CURRENCY_CODES.put("\u20AC", "eur");
    CURRENCY_CODES.put("\u00A3", "gbp");
    CURRENCY_CODES.put("\u5143", "cny");
  }

  private final boolean applyHeuristics;
  private final LocationLexicon locationLexicon;
  private final EntityLexicon entityLexicon;
  private final ParserAnnotator constituencyParser;

  public Seq2SeqTokenizer(String languageTag, boolean applyHeuristics) {
    this.applyHeuristics = applyHeuristics;

    locationLexicon = LocationLexicon.getForLanguage(languageTag);
    entityLexicon = EntityLexicon.getForLanguage(languageTag);
    
    if (INCLUDE_CONSTITUENCY_PARSE) {
      Properties parseProperties = new Properties();
      parseProperties.put("parse.model", "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz");
      parseProperties.put("parse.binaryTrees", "true");
      parseProperties.put("parse.buildgraphs", "false");
      constituencyParser = new ParserAnnotator("parse", parseProperties);
    } else {
      constituencyParser = null;
    }
  }

  private static void addEntity(Result result, String tag, int id, Object value) {
    result.entities.computeIfAbsent(new Value(tag, value), (key) -> new LinkedList<>()).add(id);
  }

  private void computeTokens(Example ex, LanguageInfo utteranceInfo, Result result) {
    Map<String, Integer> nextInt = new HashMap<>();
    List<String> fullEntity = new ArrayList<>();
    result.rawTokens.addAll(utteranceInfo.tokens);
    result.posTags.addAll(utteranceInfo.posTags);

    for (int i = 0; i < utteranceInfo.tokens.size(); i++) {
      String token, tag, current;

      tag = utteranceInfo.nerTags.get(i);
      token = utteranceInfo.tokens.get(i);

      if (!"O".equals(tag)) {
        fullEntity.add(token);
        if (i < utteranceInfo.tokens.size() - 1 &&
            utteranceInfo.nerTags.get(i + 1).equals(tag) &&
            Objects.equals(utteranceInfo.nerValues.get(i), utteranceInfo.nerValues.get(i + 1)))
          continue;

        Pair<String, Object> value = nerValueToThingTalkValue(ex, tag, utteranceInfo.nerValues.get(i),
            Joiner.on(' ').join(fullEntity));
        // ignore tt:device entities
        if (value != null && "GENERIC_ENTITY_tt:device".equals(value.first()))
          value = null;
        if (value != null) {
          if (applyHeuristics && value.second().equals(2.0) && token.equals("two")) {
            current = token;
          } else {
            tag = value.first();
            int id = nextInt.compute(tag, (oldKey, oldValue) -> {
              if (oldValue == null)
                oldValue = -1;
              return oldValue + 1;
            });
            addEntity(result, tag, id, value.second());
            current = tag + "_" + id + '*' + ("QUOTED_STRING".equals(tag) ? fullEntity.size() - 2 : fullEntity.size());

            switch (tag) {
            case "QUOTED_STRING":
              addEntity(result, "STRING", id, Joiner.on(' ').join(fullEntity));
              result.tokensNoQuotes.addAll(fullEntity.subList(1, fullEntity.size() - 1));
              break;
            case "HASHTAG":
              addEntity(result, "TAG", id, value.second());
              result.tokensNoQuotes.add((String) value.second());
              break;
            case "USERNAME":
              addEntity(result, "NAME", id, value.second());
              result.tokensNoQuotes.add((String) value.second());
              break;
            default:
              if (tag.startsWith("GENERIC_ENTITY_")) {
                addEntity(result, tag.substring("GENERIC_".length()), id, Joiner.on(' ').join(fullEntity));
                result.tokensNoQuotes.addAll(fullEntity);
              } else {
                result.tokensNoQuotes.add(current);
              }
              break;
            }
          }
        } else {
          result.tokens.addAll(fullEntity);
          result.tokensNoQuotes.addAll(fullEntity);
          current = null;
        }
      } else {
        current = token;
        result.tokensNoQuotes.add(current);
      }
      fullEntity.clear();
      if (current != null)
        result.tokens.add(current);
    }
  }

  public Result process(Example ex) {

    LanguageInfo utteranceInfo = ex.languageInfo;
    Result result = new Result();

    computeTokens(ex, utteranceInfo, result);
    computeConstituencyParse(result);

    return result;
  }

  private void computeConstituencyParse(Result result) {
    if (constituencyParser == null)
      return;

    String sentencestring = Joiner.on(' ').join(result.tokens);
    Annotation document = new Annotation(sentencestring);

    List<CoreLabel> tokens = new ArrayList<>();
    for (int i = 0; i < result.tokens.size(); i++) {
      String token = result.tokens.get(i);
      CoreLabel coreToken = new CoreLabel();
      coreToken.setWord(token);
      coreToken.setValue(token);
      coreToken.setIndex(i);
      coreToken.setNER(Character.isUpperCase(token.charAt(0)) ? token : "O");
      tokens.add(coreToken);
    }

    CoreMap sentence = new Annotation(sentencestring);
    document.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, 0);
    document.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));

    constituencyParser.annotate(document);

    Tree tree = sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class);
    linearizeTree(tree, result.constituencyParse);
  }

  private static void linearizeTree(Tree tree, List<String> linear) {
    if (tree.isLeaf()) {
      linear.add(tree.value());
    } else if (tree.numChildren() == 1) {
      linearizeTree(tree.getChild(0), linear);
    } else {
      assert tree.numChildren() == 2;
      linear.add("(");
      linearizeTree(tree.getChild(0), linear);
      linearizeTree(tree.getChild(1), linear);
      linear.add(")");
    }
  }

  private LocationValue findLocation(String entity) {
    // earth is not a location, and neiter is europe
    switch (entity) {
    case "earth":
    case "europe":
    case "uk":
    case "us":
    case "u.s.":
    case "usa":
    case "united states":
    case "united states of america":
    case "united kingdom":
    case "america":
    case "england":
    case "germany":
    case "italy":
    case "california":

      // how sabrina could be a location is beyond me
    case "sabrina":

      // or tumblr for what matters
    case "tumblr":

      // or wapo
    case "wapo":
      return null;
    }
    if ("la".equals(entity) || entity.startsWith("la , ca") || entity.startsWith("la ca"))
      entity = "los angeles";

    if (entity.startsWith("los angeles") || "l.a.".equals(entity))
      return new LocationValue(34.0543942, -118.2439408);

    Collection<LocationLexicon.Entry<LocationValue>> entries = locationLexicon.lookup(entity);
    if (entries.isEmpty())
      return null;

    LocationLexicon.Entry<LocationValue> first = entries.iterator().next();
    return first.value;
  }

  // refuse to return anything for yahoo, because otherwise every yahoo finance sentence
  // would have a very confusing two entities
  private static final Set<String> NOT_ENTITIES = Sets.newHashSet("wsj world news", "yahoo", "capital weather gang",
      "ac state", "ncaa mens");

  private static boolean tokensEquals(String one, String two) {
    if (one.equals(two))
      return true;
    if (Stemmer.stem(one).equals(Stemmer.stem(two)))
      return true;
    if (one.equals("cardinals") && two.equals("cardinal"))
      return true;
    if (one.equals("cardinal") && two.equals("cardinals"))
      return true;
    if (one.equals("yourself") && two.equals("yourselves"))
      return true;
    if (one.equals("yourselves") && two.equals("yourself"))
      return true;
    return false;
  }

  private Pair<String, Object> findEntity(Example ex, String entity, String hint, String fromTag) {
    // override the lexicon on this one
    if (applyHeuristics) {
      if (entity.equals("uber") || entity.equals("wall street journal") || entity.startsWith("uber pool")
          || entity.startsWith("sunset time") || entity.startsWith("sunrise time") ||
          entity.startsWith("lg ") || entity.equals("pool") ||
          entity.equals("washington post") || entity.equals("new york times"))
        return null;
      if (entity.equals("warriors"))
        return new Pair<>("GENERIC_ENTITY_sportradar:nba_team",
            new EntityValue("gsw", "Golden State Warriors"));
      if (entity.equals("cavaliers"))
        return new Pair<>("GENERIC_ENTITY_sportradar:nba_team",
            new EntityValue("cle", "Cleveland Cavaliers"));
      if (entity.equals("giants"))
        return new Pair<>("GENERIC_ENTITY_sportradar:mlb_team",
            new EntityValue("sf", "San Francisco Giants"));
      if (entity.equals("cubs"))
        return new Pair<>("GENERIC_ENTITY_sportradar:mlb_team",
            new EntityValue("chc", "Chicago Cubs"));
      if (entity.equals("lakers"))
        return new Pair<>("GENERIC_ENTITY_sportradar:nba_team",
            new EntityValue("lal", "Los Angeles Lakers"));
      if (entity.equals("wolf pack") || entity.equals("nevada wolf pack"))
        return new Pair<>("GENERIC_ENTITY_sportradar:ncaafb_team",
            new EntityValue("nev", "Nevada Wolf Pack"));
      // Barcellona Pozzo di Grotto, obviously
      if (entity.equals("barcelona"))
        return new Pair<>("GENERIC_ENTITY_sportradar:eu_soccer_team",
            new EntityValue("bar", "FC Barcelona"));

      if (NOT_ENTITIES.contains(entity))
        return null;
    }

    String tokens[] = entity.split("\\s+");

    Set<EntityLexicon.Entry<EntityValue>> entitySet = new HashSet<>();

    for (String token : tokens)
      entitySet.addAll(entityLexicon.lookup(token));

    if (entitySet.isEmpty())
      return null;

    // (scare quotes) MACHINE LEARNING!
    int nfootball = 0;
    int nbasketball = 0;
    int nbaseball = 0;
    int nstock = 0;
    for (String token : ex.getTokens()) {
      switch (token) {
      case "football":
      case "ncaafb":
      case "nfl":
        nfootball++;
        break;

      case "ncaambb":
      case "nba":
      case "basketball":
        nbasketball++;
        break;

      case "mlb":
      case "baseball":
        nbaseball++;
        break;

      case "stock":
      case "stocks":
      case "finance":
      case "quote":
      case "dividend":
      case "dividends":
        nstock++;
      }
    }
    if (entity.equals("california bears")) {
      if (nfootball > nbasketball)
        return new Pair<>("GENERIC_ENTITY_sportradar:ncaafb_team",
            new EntityValue("cal", "California Bears"));
      else if (nfootball < nbasketball)
        return new Pair<>("GENERIC_ENTITY_sportradar:ncaambb_team",
            new EntityValue("cal", "California Golden Bears"));
    }
    if ((entity.equals("google") || entity.equals("facebook")) && nstock == 0)
        return null;

    List<Pair<Pair<String, Object>, Double>> weights = new ArrayList<>();
    for (EntityLexicon.Entry<EntityValue> entry : entitySet) {
      String nerTag = entry.nerTag;
      EntityValue value = entry.value;
      String[] canonicalTokens = entry.rawPhrase.split("\\s+");

      if (hint != null && !nerTag.endsWith(hint))
        continue;
      if (fromTag.equals("ORGANIZATION") && !nerTag.startsWith("GENERIC_ENTITY_sportradar"))
        continue;

      double weight = 0;
      if (nerTag.endsWith("sportradar:mlb_team"))
        weight += 0.25 * nbaseball;
      else if (nerTag.endsWith("sportradar:nba_team") || nerTag.endsWith("sportradar:ncaambb_team"))
        weight += 0.25 * nbasketball;
      else if (nerTag.endsWith("sportradar:nfl_team") || nerTag.endsWith("sportradar:ncaafb_team"))
        weight += 0.25 * nfootball;

      for (String canonicalToken : canonicalTokens) {
        boolean found = false;
        for (String token : tokens) {
          if (tokensEquals(token, canonicalToken)) {
            weight += 1;
            found = true;
          } else if (token.equals("la") && (canonicalToken.equals("los") || canonicalToken.equals("angeles"))) {
            weight += 0.5;
            found = true;
          }
        }
        if (!found)
          weight -= 0.1;
      }

      weights.add(new Pair<>(new Pair<>(nerTag, value), weight));
    }
    if (weights.isEmpty())
      return null;

    weights.sort((one, two) -> {
      double w1 = one.second();
      double w2 = two.second();

      if (w1 == w2)
        return 0;
      // sort highest weight first
      if (w1 < w2)
        return +1;
      else
        return -1;
    });

    double maxWeight = weights.get(0).second();
    if (weights.size() > 1 && weights.get(1).second() == maxWeight) {
      System.out.println("Ambiguous entity " + entity + ", could be any of " + weights);
      return null;
    }

    return weights.get(0).first();
  }

  private static TimeValue parseTimeValue(String nerValue) {
    DateValue date = DateValue.parseDateValue(nerValue);
    if (date == null)
      return null;
    return new TimeValue(date.hour, date.minute);
  }

  private Pair<String, Object> nerValueToThingTalkValue(Example ex, String nerType, String nerValue,
      String entity) {
    String unit = null;

    if (nerType.startsWith("GENERIC_ENTITY_") && !nerType.equals("GENERIC_ENTITY_sportradar"))
      return findEntity(ex, entity, nerType.substring("GENERIC_ENTITY_".length()), nerType);

    switch (nerType) {
    case "MONEY":
    case "PERCENT":
      if (nerValue == null)
        return null;
      unit = nerValue.substring(0, 1);
      nerValue = nerValue.substring(1);
      // fallthrough

    case "NUMBER":
      if (nerValue == null)
        return null;
      try {
        if (nerValue.startsWith(">=") || nerValue.startsWith("<="))
          nerValue = nerValue.substring(2);
        else if (nerValue.startsWith(">") || nerValue.startsWith("<") || nerValue.startsWith("~"))
          nerValue = nerValue.substring(1);
        double v = Double.valueOf(nerValue);
        if (v == 1 || v == 0)
          return null;

        if ("MONEY".equals(nerType))
          return new Pair<>("CURRENCY", new NumberValue(v, CURRENCY_CODES.getOrDefault(unit, unit)));
        else
          return new Pair<>(nerType, v);
      } catch (NumberFormatException e) {
        return null;
      }

    case "DATE": {
      DateValue date = DateValue.parseDateValue(nerValue);
      if (date == null)
        return null;
      return new Pair<>(nerType, date);
    }
    case "TIME":
      if (nerValue == null)
        return null;
      if (!nerValue.startsWith("T")) {
        // actually this is a date, not a time
        DateValue date = DateValue.parseDateValue(nerValue);
        if (date == null)
          return null;
        return new Pair<>("DATE", date);
      } else {
        TimeValue time = parseTimeValue(nerValue);
        if (time == null)
          return null;
        return new Pair<>(nerType, time);
      }

    case "USERNAME":
    case "HASHTAG":
    case "PHONE_NUMBER":
    case "EMAIL_ADDRESS":
    case "URL":
    case "QUOTED_STRING":
    case "PATH_NAME":
      return new Pair<>(nerType, nerValue);

    case "LOCATION":
      LocationValue loc = findLocation(entity);
      if (loc == null)
        //return findEntity(ex, entity, "tt:country", nerType);
        return null;
      return new Pair<>(nerType, loc);

    //case "ORGANIZATION":
    case "GENERIC_ENTITY_sportradar":
      return findEntity(ex, entity, null, nerType);

    case "DURATION":
      if (nerValue != null) {
        NumberValue v = NumberValue.parseDurationValue(nerValue);
        if (v == null)
          return null;
        if (v.value == 1 && v.unit.equals("day"))
          return null;
        return new Pair<>(nerType, v);
      } else {
        return null;
      }
    }

    return null;
  }

}
