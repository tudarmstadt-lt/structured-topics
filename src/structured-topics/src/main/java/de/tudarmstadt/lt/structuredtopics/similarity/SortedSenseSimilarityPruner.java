package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortedSenseSimilarityPruner {

	private static final Logger LOG = LoggerFactory.getLogger(SortedSenseSimilarityPruner.class);

	/**
	 * arg 1: Sorted sense similarities (output of
	 * {@link SenseSimilarityCalculator}, sorted by the first column, then by
	 * the third column. arg 2: output-file (same format, but each sense
	 * similarities will be pruned to the third parameter) arg 3: integer,
	 * number of similar senses to keep for each sense. arg 4: true|false, if
	 * true, all similarities are replaced with 1.0 (binarized)
	 * 
	 */
	public static void main(String[] args) {
		File input = new File(args[0]);
		File output = new File(args[1]);
		int sensesToKeep = Integer.parseInt(args[2]);
		boolean binarize = Boolean.parseBoolean(args[3]);
		try (BufferedWriter out = new BufferedWriter(new FileWriter(output))) {
			try (BufferedReader in = new BufferedReader(new FileReader(input))) {
				String line = null;
				String currentSense = "";
				int currentSenseCount = 0;
				while ((line = in.readLine()) != null) {
					String[] split = line.split("\t");
					String sense = split[0];
					if (sense.equals(currentSense)) {
						currentSenseCount++;
					} else {
						// new sense
						currentSense = sense;
						currentSenseCount = 0;
					}
					if (currentSenseCount < sensesToKeep) {
						if (binarize) {
							out.write(split[0] + "\t" + split[1] + "\t" + "1.0");
						} else {
							out.write(line);
						}
						out.write("\n");
					} else {
						// prune line
					}
				}

			}
		} catch (Exception e) {
			LOG.error("Error while pruning:", e);
		}
	}
}
