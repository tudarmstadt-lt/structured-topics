package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Parser {

	public void parse(String resource) {
		System.out.println("reading");
		Map<String, Map<Integer, List<String>>> clusters = readClusters(resource);
		System.out.println("calculating sim");
		try (PrintWriter out = new PrintWriter(new File("sim.txt"))) {
			int count = 0;
			Set<Entry<String, Map<Integer, List<String>>>> entrySet = clusters.entrySet();
			for (Entry<String, Map<Integer, List<String>>> sense : entrySet) {
				if (count++ % 10000 == 0) {
					System.out.println(count + "/" + entrySet.size());
				}
				String senseName = sense.getKey();
				for (Entry<Integer, List<String>> senseIdClusters : sense.getValue().entrySet()) {
					Integer senseId = senseIdClusters.getKey();
					for (String word : senseIdClusters.getValue()) {
						Map<Integer, List<String>> possibleSensesForClusterWord = clusters.get(word);
						if (possibleSensesForClusterWord == null) {
							System.err.println("No cluster for word " + word + " ?");
							continue;
						}
						for (Entry<Integer, List<String>> senseForWord : possibleSensesForClusterWord.entrySet()) {
							Integer wordSenseId = senseForWord.getKey();
							double similarity = computeSimilarity(senseIdClusters.getValue(), senseForWord.getValue());
							if (similarity != 0)
								out.println(senseName + "#" + senseId + "\t" + word + "#" + wordSenseId + "\t"
										+ similarity);
						}
					}
				}
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
	}

	private double computeSimilarity(List<String> clusterWords1, List<String> clusterWords2) {
		List<String> common = new ArrayList<String>(clusterWords1);
		common.retainAll(clusterWords2);
		double commonWords = common.size();
		return commonWords / (clusterWords1.size() + clusterWords2.size());
	}

	private Map<String, Map<Integer, List<String>>> readClusters(String resource) {
		Map<String, Map<Integer, List<String>>> senseClusterWords = Maps.newHashMapWithExpectedSize(1000000);
		int lineNumber = 1;
		try (BufferedReader in = new BufferedReader(new FileReader(fromResource(resource)))) {
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
					System.err.println("Invalid line? " + lineNumber);
					e.printStackTrace();
					continue;
				}
				List<String> words = Lists.newArrayList();
				for (String word : split[2].split("[,]\\s")) {
					words.add(word);
				}
				if (!senseClusterWords.containsKey(sense)) {
					// create new sense
					Map<Integer, List<String>> clusters = Maps.newHashMap();
					clusters.put(senseId, words);
					senseClusterWords.put(sense, clusters);
				} else {
					// add to existing sense
					Map<Integer, List<String>> clusters = senseClusterWords.get(sense);
					clusters.put(senseId, words);
				}
				if (lineNumber % 10000 == 0) {
					System.out.println(lineNumber);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Error in line: " + lineNumber, e);
		}
		return senseClusterWords;
	}

	private File fromResource(String resource) {
		try {
			return new File(getClass().getClassLoader().getResource(resource).toURI());
		} catch (URISyntaxException e) {
			Throwables.propagate(e);
			// not reachable
			return null;
		}
	}

}
