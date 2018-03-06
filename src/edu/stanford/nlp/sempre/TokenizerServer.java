package edu.stanford.nlp.sempre;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import info.faljse.SDNotify.SDNotify;

public class TokenizerServer implements Runnable {
  private final List<String> languages;

  private final ObjectMapper object = new ObjectMapper();
  private ServerSocket server;
  private final Map<String, CoreNLPAnalyzer> analyzers = new HashMap<>();
  private final Map<String, Seq2SeqTokenizer> tokenizers = new HashMap<>();
  private final Executor threadPool = Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors());

  public static class Input {
    @JsonProperty
    int req;

    @JsonProperty
    String languageTag;

    @JsonProperty
    String utterance;
  }

  public static class Output {
    @JsonProperty
    final int req;

    @JsonProperty
    final List<String> tokens;

    @JsonProperty
    final List<String> rawTokens;

    @JsonProperty
    final List<String> pos;

    @JsonProperty
    final Map<String, Object> values = new HashMap<>();

    @JsonProperty
    final List<String> constituencyParse;

    Output(int req, Seq2SeqTokenizer.Result tokenizerResult) {
      this.req = req;
      this.tokens = tokenizerResult.tokens;
      this.rawTokens = tokenizerResult.rawTokens;
      this.pos = tokenizerResult.posTags;
      this.constituencyParse = tokenizerResult.constituencyParse;
    }
  }
  
  public static class Error {
    @JsonProperty
    final int req;
 
    @JsonProperty
    final String error;
    
    public Error(int req, String error) {
      this.req = req;
      this.error = error;
    }
  }

  private TokenizerServer(String[] languages) {
    this.languages = Arrays.asList(languages);
  }

  private synchronized void writeOutput(Writer outputStream, Output output) {
    ObjectWriter writer = object.writer().withType(Output.class);
    try {
      writer.writeValue(outputStream, output);
      outputStream.append('\n');
      outputStream.flush();
    } catch (IOException e) {
      System.err.println("Failed to write tokenizer output out: " + e.getMessage());
      e.printStackTrace(System.err);
    }
  }
  
  private synchronized void writeError(Writer outputStream, Error err) {
    ObjectWriter writer = object.writer().withType(Error.class);
    try {
      writer.writeValue(outputStream, err);
      outputStream.append('\n');
      outputStream.flush();
    } catch (IOException e) {
      System.err.println("Failed to write tokenizer output out: " + e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  private void processInput(Writer outputStream, Input input) {
    if (input.languageTag == null) {
      writeError(outputStream, new Error(input.req, "Missing language tag"));
      return;
    }
    CoreNLPAnalyzer analyzer = analyzers.get(input.languageTag);
    Seq2SeqTokenizer tokenizer = tokenizers.get(input.languageTag);

    Example ex = new Example.Builder().setUtterance(input.utterance).createExample();
    ex.preprocess(analyzer);

    Seq2SeqTokenizer.Result result = tokenizer.process(ex);
    Output output = new Output(input.req, result);

    for (Map.Entry<Seq2SeqTokenizer.Value, List<Integer>> entry : result.entities.entrySet()) {
      Seq2SeqTokenizer.Value entity = entry.getKey();
      String entityType = entity.type;
      for (int entityNum : entry.getValue()) {
        String entityToken = entityType + "_" + entityNum;
        output.values.put(entityToken, entity.value);
      }
    }

    writeOutput(outputStream, output);
  }

  private void handleConnection(Socket s) {
    try (Socket socket = s) {
      Reader inputStream = new InputStreamReader(socket.getInputStream());
      Writer outputStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

      ObjectReader objReader = object.reader().withType(Input.class);
      JsonParser parser = object.getFactory().createParser(inputStream);

      while (!socket.isClosed()) {
        JsonToken nextToken = parser.nextToken();
        if (nextToken == null) {
          // eof
          break;
        }
        Input next;
        try {
          next = objReader.readValue(parser);
        } catch (JsonProcessingException e) {
          System.err.println("Invalid JSON input: " + e.getMessage());
          e.printStackTrace();
          continue;
        }

        threadPool.execute(() -> processInput(outputStream, next));
      }
    } catch (EOFException e) {
      return;
    } catch (IOException e) {
      System.err.println("IO error on connection: " + e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  @Override
  public void run() {
    for (String lang : languages) {
      analyzers.put(lang, new CoreNLPAnalyzer(lang));
      tokenizers.put(lang, new Seq2SeqTokenizer(lang, true));
    }

    object.getFactory()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

    try {
      server = new ServerSocket(8888);
      SDNotify.sendNotify();

      while (true) {
        Socket socket = server.accept();
        new Thread(() -> handleConnection(socket)).start();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    TokenizerServer server = new TokenizerServer(args);
    server.run();
  }
}
