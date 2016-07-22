package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;

public class Parser {

	private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

	public Map<String, Map<Integer, List<Feature>>> readClusters(File input, InputMode mode) {
		LOG.info("Reading clusters from {}", input.getAbsolutePath());
		Map<String, Map<Integer, List<Feature>>> senseClusterWords = Maps.newHashMapWithExpectedSize(1000000);
		int lineNumber = 1;
		try (BufferedReader in = Utils.openReader(input, mode)) {
			String line = null;
			// skip first line
			line = in.readLine();
			while ((line = in.readLine()) != null) {
				lineNumber++;
				addClusterFromLine(line, lineNumber, senseClusterWords);
				if (lineNumber % 10000 == 0) {
					LOG.info("Progess, line {}", lineNumber);
				}
			}
		} catch (Exception e) {
			LOG.error("Line {} seems to be invalid which caused an error", lineNumber, e);
		}
		return senseClusterWords;
	}

	@VisibleForTesting
	protected void addClusterFromLine(String line, int lineNumber,
			Map<String, Map<Integer, List<Feature>>> senseClusterWords) {
		String[] columns = line.split("\\t");
		if (columns.length < 3) {
			LOG.warn("Line {} seems to be invalid (missing columns):\n'{}", lineNumber, line);
			return;
		}
		String sense = columns[0];
		Integer senseId = null;
		try {
			senseId = Integer.valueOf(columns[1]);
		} catch (NumberFormatException e) {
			LOG.warn("Line {} seems to be invalid (sense id number):\n'{}", lineNumber, line);
			return;
		}
		List<Feature> features = Lists.newArrayList();
		String[] featuresRaw = columns[2].split(",\\s*");
		for (int i = 0; i < featuresRaw.length; i++) {
			String rawFeature = featuresRaw[i];
			// features have either the format
			// 'word#posTag#index:weight' or 'word#posTag'
			// weight is the last part, avoid splitting words containing
			// '#'
			int lastHash = rawFeature.lastIndexOf("#");
			String word;
			String weightString = "";
			if (lastHash <= 0) {
				// too noise, use only for debugging purpose
				// LOG.warn("Missing hash in line {}", lineNumber);
				continue;
			}
			// weight or postag
			weightString = rawFeature.substring(lastHash + 1, rawFeature.length());
			String[] wordSenseIdWeight = weightString.split(":");
			double weight;
			Integer wordSenseId = null;
			if (wordSenseIdWeight.length == 2) {
				word = rawFeature.substring(0, lastHash).trim();
				try {
					wordSenseId = Integer.parseInt(wordSenseIdWeight[0]);
					weight = Double.valueOf(wordSenseIdWeight[1]);
				} catch (NumberFormatException e) {
					LOG.warn("Line {} seems to be invalid (feature weight):\n'{}", lineNumber, line);
					weight = 1;
				}
			} else {
				// no weights
				// +1 -> avoid weight 0 for last word
				weight = ((double) featuresRaw.length - i + 1) / (featuresRaw.length + 1);
				word = rawFeature;
			}
			features.add(new Feature(word.trim(), weight, wordSenseId));
		}
		addSenseCluster(senseClusterWords, sense, senseId, features);
	}

	private Map<Integer, List<Feature>> addSenseCluster(Map<String, Map<Integer, List<Feature>>> senseClusterWords,
			String sense, Integer senseId, List<Feature> words) {
		Map<Integer, List<Feature>> clusters = null;
		if (senseClusterWords.containsKey(sense)) {
			// add to existing sense
			clusters = senseClusterWords.get(sense);
		} else {
			// create new sense
			clusters = Maps.newHashMap();
			senseClusterWords.put(sense, clusters);
		}
		clusters.put(senseId, words);
		return clusters;
	}

	public Map<String, Integer> readWordFrequencies(File input, InputMode mode) {
		Map<String, Integer> frequencies = Maps.newHashMap();
		String line = null;
		try (BufferedReader in = Utils.openReader(input, mode)) {
			in.readLine();// skip header
			while ((line = in.readLine()) != null) {
				String[] split = line.split("\\t");
				if (split.length != 2) {
					LOG.warn("Invalid line: {}", line);
					continue;
				}
				if (frequencies.containsKey(split[0])) {
					LOG.warn("Duplicate value: {}", split[0]);
					frequencies.put(split[0], Integer.valueOf(split[1]) + frequencies.get(split[0]));
				}
				frequencies.put(split[0], Integer.valueOf(split[1]));
			}

		} catch (IOException e) {
			LOG.error("Error in line: {}, {}", line, e);
		}
		return frequencies;
	}

}
