package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class SortedSenseSimilarityPruner {

	private static final Logger LOG = LoggerFactory.getLogger(SortedSenseSimilarityPruner.class);

	/**
	 * arg 1: Sorted sense similarities (output of
	 * {@link SenseSimilarityCalculator}, sorted by the first column, then by
	 * the third column. arg 2: output-file (same format, but each sense
	 * similarities will be pruned to the third parameter) arg 3: integer,
	 * number of similar senses to keep for each sense. arg 4: true|false, if
	 * true, all similarities are replaced with 1.0 (binarized) arg 5:
	 * (otional): double value, if the similarity drops below this factor to the
	 * top score, all further similar senses are pruned
	 * 
	 */
	public static void main(String[] args) {
		File input = new File(args[0]);
		File output = new File(args[1]);
		int sensesToKeep = Integer.parseInt(args[2]);
		boolean binarize = Boolean.parseBoolean(args[3]);
		double similarityThreshold = Double.MAX_VALUE;
		if (args.length >= 5) {
			similarityThreshold = Double.parseDouble(args[4]);
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
		} catch (Exception e) {
			LOG.error("Error while pruning:", e);
		}
	}
}
