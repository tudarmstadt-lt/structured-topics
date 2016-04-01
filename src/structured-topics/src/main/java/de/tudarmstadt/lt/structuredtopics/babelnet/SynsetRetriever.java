package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Retrieves the synset ids for a list of words. Calls to the api are cached.
 * Words without at least one alphabetic character are skipped.
 *
 */
public class SynsetRetriever {

	private static final Logger LOG = LoggerFactory.getLogger(SynsetRetriever.class);

	private static final String OPTION_API_KEY = "key";
	private static final String OPTION_API_CACHE = "apiCache";
	private static final String OPTION_FILE_WORDS = "wordList";
	private static final String OPTION_FILE_OUT = "out";

	public static final String WORD_REGEX = ".*[a-zA-Z]+.*";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File cacheLocation = new File(cl.getOptionValue(OPTION_API_CACHE));
			String apiKey = cl.getOptionValue(OPTION_API_KEY);
			File wordList = new File(cl.getOptionValue(OPTION_FILE_WORDS));
			if (!wordList.exists()) {
				LOG.error("word list {} not found", wordList.getAbsolutePath());
				return;
			}
			File out = new File(cl.getOptionValue(OPTION_FILE_OUT));
			if (out.exists()) {
				LOG.error("{} exists, please use a different file for output", out.getAbsolutePath());
				return;
			}
			CachingApi api = new RetryCallCachingApi(cacheLocation, apiKey);
			retrieveSynsetIds(api, wordList, out);
		} catch (ParseException e) {
			LOG.error("Invalid arguments: {}", e.getMessage());
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("Error", e);
		}
	}

	private static void retrieveSynsetIds(CachingApi api, File wordList, File outFile) throws IOException {
		Gson gson = new Gson();
		try (BufferedReader in = new BufferedReader(new FileReader(wordList))) {
			try (BufferedWriter out = new BufferedWriter(new FileWriter(outFile))) {
				String word = null;
				while ((word = in.readLine()) != null) {
					String synsetIds = "";
					if (word.trim().matches(WORD_REGEX)) {
						try {
							String sensesJson = api.getSenses(word);
							List json = (List) gson.fromJson(sensesJson, Object.class);
							for (Object sense : json) {
								Map synsetIdValues = (Map) ((Map) sense).get("synsetID");
								String synsetId = (String) synsetIdValues.get("id");
								if (synsetId != null) {
									synsetIds += synsetId + ", ";
								}
							}
						} catch (Exception e) {
							LOG.error("Error while retrieving synset ids for word {}", word, e);
						}
					}
					out.write(word + "\t" + synsetIds + "\n");
					out.flush();
				}
			}
		}

	}

	private static Options createOptions() {
		Options options = new Options();
		Option apiKey = Option.builder(OPTION_API_KEY).argName("api key").desc("The api key").hasArg().required()
				.build();
		options.addOption(apiKey);
		Option apiCache = Option.builder(OPTION_API_CACHE).argName("apiCache")
				.desc("Folder where api calls will be cached").required().hasArg().build();
		options.addOption(apiCache);
		Option fileWords = Option.builder(OPTION_FILE_WORDS).argName("fileWords")
				.desc("The file with the input words, expected is one word per line").hasArg().required().build();
		options.addOption(fileWords);
		Option out = Option.builder(OPTION_FILE_OUT).argName("out")
				.desc("File where the words with their synset ids will be stored (one word + synsetid per line, tab separated)")
				.hasArg().required().build();
		options.addOption(out);
		return options;
	}
}
