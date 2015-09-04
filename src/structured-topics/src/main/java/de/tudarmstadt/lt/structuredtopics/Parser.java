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

	public Map<String, Map<Integer, List<Feature>>> readClusters(File input, InputMode mode) {
		Map<String, Map<Integer, List<Feature>>> senseClusterWords = Maps.newHashMapWithExpectedSize(1000000);
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
				List<Feature> features = Lists.newArrayList();
				String[] featuresRaw = split[2].split("[,]\\s");
				for (int i = 0; i < featuresRaw.length; i++) {
					// TODO read real weights (needs new data)
					String word = featuresRaw[i];
					double weight = featuresRaw.length - i;
					features.add(new Feature(word, weight));
				}
				addSenseCluster(senseClusterWords, sense, senseId, features);
				if (lineNumber % 10000 == 0) {
					LOG.info("Progess, line {}", lineNumber);
				}
			}
		} catch (Exception e) {
			LOG.error("Line {} seems to be invalid which caused an error", lineNumber, e);
		}
		return senseClusterWords;
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

}
