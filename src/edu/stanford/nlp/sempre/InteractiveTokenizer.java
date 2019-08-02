package edu.stanford.nlp.sempre;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.logging.Redwood;

public class InteractiveTokenizer implements Runnable {
  private static final Redwood.RedwoodChannels log = Redwood.channels(InteractiveTokenizer.class);

  public static void main(String[] args) {
    InteractiveTokenizer tokenizer = new InteractiveTokenizer();
    tokenizer.run();
  }

  @Override
  public void run() {
    CoreNLPAnalyzer analyzer = new CoreNLPAnalyzer(new LocaleTag("en"));
    Seq2SeqTokenizer tokenizer = new Seq2SeqTokenizer(true);

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      while (true) {
        System.out.println("Enter some text:");
        String text = reader.readLine();
        if (text == null)
          break;
        Redwood.startTrack();
        log.logf("Analyzing \"%s\"", text);
        Example ex = new Example.Builder().setUtterance(text).createExample();
        ex.preprocess(analyzer);

        Seq2SeqTokenizer.Result result = tokenizer.process(ex);
        log.logf("tokens: %s", result.tokens);
        log.logf("pos tags: %s", result.posTags);

        for (Map.Entry<Value, List<Integer>> entry : result.entities.entrySet()) {
          Value entity = entry.getKey();
          String entityType = entity.type;
          for (int entityNum : entry.getValue()) {
            String entityToken = entityType + "_" + entityNum;
            log.logf("%s = %s", entityToken, entity.value);
          }
        }
        Redwood.endTrack();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
