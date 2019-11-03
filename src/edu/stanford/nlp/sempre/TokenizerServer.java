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

public class TokenizerServer {
  private static final int DEFAULT_PORT = 8888;

  private final ObjectMapper object = new ObjectMapper();
  private final ServerSocket server;
  private final Map<LocaleTag, CoreNLPAnalyzer> analyzers = new HashMap<>();
  private final Seq2SeqTokenizer tokenizer = new Seq2SeqTokenizer(true);
  private final Executor threadPool = Executors.newWorkStealingPool();

  public static class Input {
    @JsonProperty
    int req;

    @JsonProperty(value="languageTag")
    String localeTag;

    @JsonProperty
    String utterance;

    @JsonProperty
    String expect;
  }

  public static class Output {
    @JsonProperty
    final int req;

    @JsonProperty
    final List<String> tokens;

    @JsonProperty
    final List<String> tokensNoQuotes;

    @JsonProperty
    final List<String> rawTokens;

    @JsonProperty
    final List<String> pos;

    @JsonProperty
    final Map<String, Object> values = new HashMap<>();

    @JsonProperty
    final List<String> constituencyParse;

    @JsonProperty
    final String sentiment;

    Output(int req, Seq2SeqTokenizer.Result tokenizerResult) {
      this.req = req;
      this.tokens = tokenizerResult.tokens;
      this.rawTokens = tokenizerResult.rawTokens;
      this.tokensNoQuotes = tokenizerResult.tokensNoQuotes;
      this.pos = tokenizerResult.posTags;
      this.constituencyParse = tokenizerResult.constituencyParse;
      this.sentiment = tokenizerResult.sentiment;
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

  private TokenizerServer(int port, String[] localeTags) throws IOException {
    LocaleTag[] locales = new LocaleTag[localeTags.length];
    for (int i = 0; i < localeTags.length; i++) {
      String localeTag = localeTags[i];
      locales[i] = new LocaleTag(localeTag);
      analyzers.put(locales[i], new CoreNLPAnalyzer(locales[i]));
    }

    object.getFactory()
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
        .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);

    server = new ServerSocket(port);
  }

  public void run() throws IOException {
    while (true) {
      Socket socket = server.accept();
      new Thread(() -> handleConnection(socket)).start();
    }
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
    if (input.localeTag == null) {
      writeError(outputStream, new Error(input.req, "Missing locale tag"));
      return;
    }
    LocaleTag localeTag = new LocaleTag(input.localeTag);
    CoreNLPAnalyzer analyzer = null;
    for (LocaleTag fallback : localeTag.getFallbacks()) {
      analyzer = analyzers.get(fallback);
      if (analyzer != null)
        break;
    }
    if (analyzer == null) {
      writeError(outputStream, new Error(input.req, "Unsupported locale tag"));
      return;
    }

    Output output;
    try {
      Example ex = new Example.Builder().setUtterance(input.utterance).setExpected(input.expect).createExample();
      ex.preprocess(analyzer);

      Seq2SeqTokenizer.Result result = tokenizer.process(ex);
      output = new Output(input.req, result);

      for (Map.Entry<Value, List<Integer>> entry : result.entities.entrySet()) {
        Value entity = entry.getKey();
        String entityType = entity.type;
        for (int entityNum : entry.getValue()) {
          String entityToken = entityType + "_" + entityNum;
          output.values.put(entityToken, entity.value);
        }
      }
    } catch(Throwable t) {
      writeError(outputStream, new Error(input.req, t.toString()));
      t.printStackTrace();
      return;
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

  public static void main(String[] args) {
    int port = DEFAULT_PORT;
    if (args.length >= 2 && "--port".equals(args[0])) {
      port = Integer.parseInt(args[1]);
      args = Arrays.copyOfRange(args, 2, args.length);
    }

    try {
      TokenizerServer server = new TokenizerServer(port, args);
      server.run();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
