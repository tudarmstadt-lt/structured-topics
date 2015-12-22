package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class SortedSenseSimilarityPruner {

	private static final Logger LOG = LoggerFactory.getLogger(SortedSenseSimilarityPruner.class);
	private static final String OPTION_IN_FILE = "in";
	private static final String OPTION_OUT_FILE = "out";
	private static final String OPTION_SENSES_TO_KEEP = "sensesToKeep";
	private static final String OPTION_BINARIZE = "binarize";
	private static final String OPTION_SIMILARITY_THRESHOLD = "similarityThreshold";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File input = new File(cl.getOptionValue(OPTION_IN_FILE));
			File output = new File(cl.getOptionValue(OPTION_OUT_FILE));
			int sensesToKeep = Integer.parseInt(cl.getOptionValue(OPTION_SENSES_TO_KEEP));
			boolean binarize = Boolean.parseBoolean(cl.getOptionValue(OPTION_BINARIZE));
			double similarityThreshold = Double.MAX_VALUE;
			if (cl.hasOption(OPTION_SIMILARITY_THRESHOLD)) {
				similarityThreshold = Double.parseDouble(cl.getOptionValue(OPTION_SIMILARITY_THRESHOLD));
			}
			try (BufferedWriter out = Utils.openGzipWriter(output)) {
				try (BufferedReader in = Utils.openReader(input, InputMode.GZ)) {
					String line = null;
					String currentSense = "";
					int currentSenseCount = 0;
					while ((line = in.readLine()) != null) {
						try {
							String[] split = line.split("\t");
							String sense = split[0];
							double topSimilarity = 0;
							double currentSimilarity = Double.parseDouble(split[2]);
							if (sense.equals(currentSense)) {
								currentSenseCount++;
							} else {
								// new sense
								currentSense = sense;
								currentSenseCount = 0;
								topSimilarity = currentSimilarity;
							}
							if (currentSenseCount < sensesToKeep
									&& (topSimilarity / currentSimilarity) < similarityThreshold) {
								if (binarize) {
									out.write(split[0] + "\t" + split[1] + "\t" + "1.0");
								} else {
									out.write(line);
								}
								out.write("\n");
							} else {
								// prune line
							}
						} catch (Exception e) {
							LOG.error("Error while pruning line {}", line, e);
						}
					}

				}
			}
		} catch (ParseException e) {
			LOG.error("Invalid arguments", e);
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("Error while pruning:", e);
		}
	}

	private static Options createOptions() {
		Options options = new Options();
		Option input = Option.builder(OPTION_IN_FILE).argName("file")
				.desc("Sorted sense similarities (output of\n"
						+ "	 * {@link SenseSimilarityCalculator}, sorted by the first column, then by\n"
						+ "	 * the third column")
				.hasArg().required().type(String.class).required().build();
		options.addOption(input);
		Option output = Option.builder(OPTION_OUT_FILE).argName("output file").desc("Path to the output file").hasArg()
				.required().type(String.class).build();
		options.addOption(output);
		Option sensesToKeep = Option.builder(OPTION_SENSES_TO_KEEP).argName("senses to keep")
				.desc("top n similar senses to keep for each sense").hasArg().type(Integer.class).build();
		options.addOption(sensesToKeep);
		Option similarityThreshold = Option.builder(OPTION_SIMILARITY_THRESHOLD).argName("similarity threshold")
				.desc("Additional pruning: If the similarity drops below this threshold (factor to the top similarity), all further senses are pruned.")
				.hasArg().type(Integer.class).build();
		options.addOption(similarityThreshold);
		return options;
	}
}
