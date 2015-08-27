package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedReader;
import java.io.File;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;

public class Parser {

	private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

	public Map<String, Map<Integer, List<String>>> readClusters(File input, InputMode mode) {
		Map<String, Map<Integer, List<String>>> senseClusterWords = Maps.newHashMapWithExpectedSize(1000000);
		int lineNumber = 1;
		try (BufferedReader in = Utils.openReader(input, mode)) {
			String line = null;
			// skip first line
			line = in.readLine();
			while ((line = in.readLine()) != null) {
				lineNumber++;
				String[] split = line.split("\\t");
				String sense = split[0];
				Integer senseId = null;
				try {
					senseId = Integer.valueOf(split[1]);
				} catch (NumberFormatException e) {
					LOG.warn("Line {} seems to be invalid:\n'{}", lineNumber, line);
					continue;
				}
				List<String> words = Lists.newArrayList();
				for (String word : split[2].split("[,]\\s")) {
					words.add(word);
				}
				addSenseCluster(senseClusterWords, sense, senseId, words);
				if (lineNumber % 10000 == 0) {
					LOG.info("Progess, line {}", lineNumber);
				}
			}
		} catch (Exception e) {
			LOG.error("Line {} seems to be invalid which caused an error", lineNumber, e);
		}
		return senseClusterWords;
	}

	private Map<Integer, List<String>> addSenseCluster(Map<String, Map<Integer, List<String>>> senseClusterWords,
			String sense, Integer senseId, List<String> words) {
		Map<Integer, List<String>> clusters = null;
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

}
