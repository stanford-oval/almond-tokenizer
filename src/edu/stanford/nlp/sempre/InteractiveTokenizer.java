package edu.stanford.nlp.sempre;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import fig.basic.LogInfo;
import fig.exec.Execution;

public class InteractiveTokenizer implements Runnable {
  public static void main(String[] args) {
    Execution.run(args, "Main", new InteractiveTokenizer(), TokenizerServer.getOptionsParser());
  }

  @Override
  public void run() {
    CoreNLPAnalyzer analyzer = new CoreNLPAnalyzer("en");
    Seq2SeqTokenizer tokenizer = new Seq2SeqTokenizer("en", true);

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      while (true) {
        System.out.println("Enter some text:");
        String text = reader.readLine();
        if (text == null)
          break;
        LogInfo.begin_track("Analyzing \"%s\"", text);
        Example ex = new Example.Builder().setUtterance(text).createExample();
        ex.preprocess(analyzer);

        Seq2SeqTokenizer.Result result = tokenizer.process(ex);
        LogInfo.logs("tokens: %s", result.tokens);
        LogInfo.logs("pos tags: %s", result.posTags);

        for (Map.Entry<Seq2SeqTokenizer.Value, List<Integer>> entry : result.entities.entrySet()) {
          Seq2SeqTokenizer.Value entity = entry.getKey();
          String entityType = entity.type;
          for (int entityNum : entry.getValue()) {
            String entityToken = entityType + "_" + entityNum;
            LogInfo.logs("%s = %s", entityToken, entity.value);
          }
        }
        LogInfo.end_track();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
