package edu.stanford.nlp.sempre;

import java.util.*;

import com.google.common.base.Joiner;
import edu.stanford.nlp.util.Pair;

public class Seq2SeqTokenizer {
  public static class Result {
    public final List<String> rawTokens = new ArrayList<>();
    public final List<String> tokens = new ArrayList<>();
    public final List<String> tokensNoQuotes = new ArrayList<>();
    public final List<String> posTags = new ArrayList<>();
    public final Map<Value, List<Integer>> entities = new HashMap<>();
    public String sentiment = "neutral";
  }

  private static final Map<String, String> CURRENCY_CODES = new HashMap<>();

  static {
    CURRENCY_CODES.put("$", "usd");
    CURRENCY_CODES.put("\u20AC", "eur");
    CURRENCY_CODES.put("\u00A3", "gbp");
    CURRENCY_CODES.put("\u5143", "cny");
  }

  public Seq2SeqTokenizer() {
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
        if (value != null) {
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
            if (fullEntity.size() >= 3)
              result.tokensNoQuotes.addAll(fullEntity.subList(1, fullEntity.size() - 1));
            break;
          case "HASHTAG":
            result.tokensNoQuotes.add((String) value.second());
            break;
          case "USERNAME":
            result.tokensNoQuotes.add((String) value.second());
            break;
          default:
            result.tokensNoQuotes.add(current);
            break;
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
    result.sentiment = ex.languageInfo.sentiment;

    return result;
  }

  private static TimeValue parseTimeValue(String nerValue) {
    DateValue date = DateValue.parseDateValue(nerValue);
    if (date == null)
      return null;
    return new TimeValue(date.hour, date.minute, date.second);
  }

  private Pair<String, Object> nerValueToThingTalkValue(Example ex, String nerType, String nerValue,
      String entity) {
    String unit = null;

    switch (nerType) {
    case "MONEY":
    case "PERCENT":
      if (nerValue == null)
        return null;
      unit = nerValue.substring(0, 1);
      nerValue = nerValue.substring(1);
      // fallthrough

    case "NUMBER":
    case "ORDINAL":
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
          return new Pair<>("NUMBER", v);
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
