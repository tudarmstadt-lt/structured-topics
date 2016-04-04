package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import de.tudarmstadt.lt.structuredtopics.Feature;
import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;

/**
 * Prints different statistics for a dtt to the log.
 *
 */
public class DDTStatistics {

	private static final Logger LOG = LoggerFactory.getLogger(DDTStatistics.class);

	private static final String OPTION_DDT = "in";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File in = new File(cl.getOptionValue(OPTION_DDT));
			if (!in.exists()) {
				LOG.error("File not found: {}", in.getAbsolutePath());
				return;
			}
			Parser parser = new Parser();
			Map<String, Map<Integer, List<Feature>>> clusters = parser.readClusters(in, InputMode.GZ);
			StringBuilder b = new StringBuilder();
			printStatistics(clusters, b);
			LOG.info("Statistics for {}:\n{}", in.getAbsolutePath(), b.toString());
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

	private static void printStatistics(Map<String, Map<Integer, List<Feature>>> clusters, StringBuilder b) {
		b.append("Unique sense words: " + clusters.size() + "\n");
		int totalSenses = 0;
		for (Entry<String, Map<Integer, List<Feature>>> cluster : clusters.entrySet()) {
			totalSenses += cluster.getValue().size();
		}
		b.append("Total senses: " + totalSenses + "\n");
		Set<String> uniqueWords = Sets.newHashSetWithExpectedSize(clusters.size() * 1000);
		int totalWords = 0;
		for (Entry<String, Map<Integer, List<Feature>>> cluster : clusters.entrySet()) {
			for (Entry<Integer, List<Feature>> sense : cluster.getValue().entrySet()) {
				totalWords += sense.getValue().size();
				for (Feature f : sense.getValue()) {
					uniqueWords.add(f.getWord());
				}
			}
		}
		b.append("Unique words: " + uniqueWords.size() + "\n");
		b.append("Total words: " + totalWords + "\n");
	}

	private static Options createOptions() {
		Options options = new Options();
		Option ddt = Option.builder(OPTION_DDT).argName("ddt file").desc("The input ddt").hasArg().required().build();
		options.addOption(ddt);
		return options;
	}
}
