package de.tudarmstadt.lt.structuredtopics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class Main {

	private static final String OPTION_INPUT_FILE_NAME = "f";
	private static final String OPTION_OUTPUT_FILE_NAME = "o";
	private static final String OPTION_DEBUG_NAME = "d";
	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	public static enum InputMode {
		TXT, GZ
	}

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine line = new DefaultParser().parse(options, args, true);
			String inputPath = (String) line.getParsedOptionValue(OPTION_INPUT_FILE_NAME);
			InputMode inputMode = inputPath.endsWith(".gz") ? InputMode.GZ : InputMode.TXT;
			File input = new File(inputPath);
			if (!input.exists()) {
				throw new FileNotFoundException("Input file not found");
			}
			String outputPath = line.hasOption(OPTION_OUTPUT_FILE_NAME)
					? (String) line.getParsedOptionValue(OPTION_OUTPUT_FILE_NAME) : null;
			LocalTime now = LocalTime.now();
			File output = new File(outputPath,
					input.getName() + now.getHour() + "_" + now.getMinute() + "_" + now.getSecond() + "-sim.gz");
			boolean debug = line.hasOption(OPTION_DEBUG_NAME);
			LOG.info("Running with setting:\n" + "input: {}\n" + "mode: {}\n" + "output: {}\n" + "debug: {}\n",
					input.getAbsolutePath(), inputMode, output.getAbsolutePath(), debug);

			Stopwatch watch = Stopwatch.createStarted();
			LOG.debug("Reading clusters");
			Parser parser = new Parser();
			Map<String, Map<Integer, List<String>>> clusters = parser.readClusters(input, inputMode);
			LOG.info("Calculating similarities");
			SimilarityCalculator similarityCalculator = new SimilarityCalculator();
			similarityCalculator.calculateSimilarities(clusters, output, debug);
			LOG.info("Finished after {}s", watch.elapsed(TimeUnit.SECONDS));

		} catch (ParseException e) {
			LOG.error("Invalid arguments", e);
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("An error ocurred", e);
		}
	}

	private static Options createOptions() {
		Options options = new Options();
		Option input = Option.builder(OPTION_INPUT_FILE_NAME).argName("file")
				.desc("Path to the csv file with DDT data, may be a .txt or txt.gz file").hasArg().type(String.class)
				.required().build();
		options.addOption(input);
		Option output = Option.builder(OPTION_OUTPUT_FILE_NAME).argName("output file")
				.desc("Path to the output file dir").hasArg().type(String.class).build();
		options.addOption(output);
		Option debug = Option.builder(OPTION_DEBUG_NAME).argName("debug").desc("Flag for debug output").build();
		options.addOption(debug);
		return options;
	}

}
