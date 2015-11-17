package de.tudarmstadt.lt.structuredtopics.convert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.lt.structuredtopics.Feature;
import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class WordFrequencyConverter {

	private static final Logger LOG = LoggerFactory.getLogger(WordFrequencyConverter.class);

	/**
	 * Creates additional files to compute
	 * https://github.com/tudarmstadt-lt/noun-sense-induction-scala#compute-dt-
	 * noun-sense-induction-scala on a ddt given the word-frequencies.
	 * <p>
	 * first arg: input-file (DDT csv in gz format), second arg:
	 * word-frequency(gz), third arg: output-file-path
	 */
	public static void main(String[] args) {
		Parser parser = new Parser();
		File input = new File(args[0]);
		File wordFrequenciesFile = new File(args[1]);
		File senseClusterWordCounts = new File(args[2], "senseClusterWordCounts.gz");
		File senseCounts = new File(args[2], "senseCounts.gz");
		LOG.info("Input: {}\n Word-Freq: {}\n Output1: {}\n Output2: {}", input.getAbsolutePath(),
				wordFrequenciesFile.getAbsolutePath(), senseClusterWordCounts.getAbsolutePath(),
				senseCounts.getAbsolutePath());
		Map<String, Integer> wordFrequencies = parser.readWordFrequencies(wordFrequenciesFile, InputMode.GZ);
		Map<String, Map<Integer, List<Feature>>> clusters = parser.readClusters(input, InputMode.GZ);
		LOG.info("Filtering clusters, size before: {}", clusters.size());
		Utils.filterClustersByPosTag(clusters);
		LOG.info("Filtered clusters, size after: {}", clusters.size());
		int total = clusters.size();
		int count = 0;
		LOG.info("Starting conversion for senseClusterWordCounts");
		try (BufferedWriter out = Utils.openGzipWriter(senseClusterWordCounts)) {
			for (Entry<String, Map<Integer, List<Feature>>> cluster : clusters.entrySet()) {
				if (count++ % 10000 == 0) {
					LOG.info("Progress: {}/{}", count, total);
				}
				String senseWord = cluster.getKey();
				Map<Integer, List<Feature>> senses = cluster.getValue();
				for (Entry<Integer, List<Feature>> sense : senses.entrySet()) {
					Integer senseId = sense.getKey();
					for (Feature feature : sense.getValue()) {
						out.append(senseWord + "#" + senseId + "\t" + feature.getWord() + "\t" + 10);
						out.append("\n");
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOG.info("Starting conversion for featureCounts");
		count = 0;
		try (BufferedWriter out = Utils.openGzipWriter(senseCounts)) {
			for (Entry<String, Map<Integer, List<Feature>>> cluster : clusters.entrySet()) {
				if (count++ % 10000 == 0) {
					LOG.info("Progress: {}/{}", count, total);
				}
				String senseWord = cluster.getKey();
				Map<Integer, List<Feature>> senses = cluster.getValue();
				for (Entry<Integer, List<Feature>> sense : senses.entrySet()) {
					int sum = 0;
					for (Feature f : sense.getValue()) {
						Integer wordFrequency = wordFrequencies.get(f.getWord());
						if (wordFrequency != null) {
							sum += wordFrequency.intValue();
						} else {
							// no hit -> assume word has frequency 1
							sum += 1;
						}
					}
					Integer senseId = sense.getKey();
					out.append(senseWord + "#" + senseId + "\t" + sum);
					out.append("\n");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOG.info("Finished, results available at {} and {}", senseClusterWordCounts.getAbsolutePath(),
				senseCounts.getAbsolutePath());
	}

}
