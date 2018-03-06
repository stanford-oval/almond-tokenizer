package edu.stanford.nlp.sempre;

import java.io.*;
import java.lang.reflect.Field;
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
import com.google.common.collect.Lists;

import fig.basic.IOUtils;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.exec.Execution;
import info.faljse.SDNotify.SDNotify;

public class TokenizerServer implements Runnable {
  public static class Options {
    @Option
    public List<String> languages = Lists.newArrayList("en", "it", "zh");
  };

  public static Options opts = new Options();

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
    for (String lang : opts.languages) {
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

  public static OptionsParser getOptionsParser() {
    OptionsParser parser = new OptionsParser();
    // Dynamically figure out which options we need to load
    // To specify this:
    //   java -Dmodules=core,freebase
    List<String> modules = Arrays.asList(System.getProperty("modules", "core").split(","));

    // All options are assumed to be of the form <class>opts.
    // Read the module-classes.txt file, which specifies which classes are
    // associated with each module.
    List<Object> args = new ArrayList<>();
    for (String line : IOUtils.readLinesHard("module-classes.txt")) {

      // Example: core edu.stanford.nlp.sempre.Grammar
      String[] tokens = line.split(" ");
      if (tokens.length != 2)
        throw new RuntimeException("Invalid: " + line);
      String module = tokens[0];
      String className = tokens[1];
      if (!modules.contains(tokens[0]))
        continue;

      // Group (e.g., Grammar)
      String[] classNameTokens = className.split("\\.");
      String group = classNameTokens[classNameTokens.length - 1];

      // Object (e.g., Grammar.opts)
      Object opts = null;
      try {
        for (Field field : Class.forName(className).getDeclaredFields()) {
          if (!"opts".equals(field.getName()))
            continue;
          opts = field.get(null);
        }
      } catch (Throwable t) {
        System.out.println("Problem processing: " + line);
        throw new RuntimeException(t);
      }

      if (opts != null) {
        args.add(group);
        args.add(opts);
      }
    }

    parser.registerAll(args.toArray(new Object[0]));
    return parser;
  }

  public static void main(String[] args) {
    Execution.run(args, "Main", new TokenizerServer(), getOptionsParser());
  }
}
