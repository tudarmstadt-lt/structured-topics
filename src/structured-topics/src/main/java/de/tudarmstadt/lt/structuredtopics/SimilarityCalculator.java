package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

public class SimilarityCalculator {

	private static final Logger LOG = LoggerFactory.getLogger(SimilarityCalculator.class);

	public void calculateSimilarities(Map<String, Map<Integer, List<String>>> clusters, File output, boolean debug) {
		try (BufferedWriter out = Utils.openWriter(output)) {
			writeSimilarities(clusters, out, debug);
		} catch (Exception e) {
			Throwables.propagate(e);
		}
	}

	private void writeSimilarities(Map<String, Map<Integer, List<String>>> clusters, BufferedWriter out, boolean debug)
			throws IOException {
		int count = 0;
		Stopwatch watch = Stopwatch.createStarted();
		Set<Entry<String, Map<Integer, List<String>>>> entrySet = clusters.entrySet();
		int total = entrySet.size();
		for (Entry<String, Map<Integer, List<String>>> sense : entrySet) {
			if (count++ % 1000 == 0) {
				double progress = (double) count / total;
				long elapsed = watch.elapsed(TimeUnit.SECONDS);
				long estimatedRemaining = (long) (elapsed * (1 / progress)) - elapsed;
				LOG.info("Similarity, progress {}/{}. Approximately finished at {}", count, total,
						LocalTime.now().plusSeconds(estimatedRemaining).toString());
			}
			String senseName = sense.getKey();
			writeSimilaritiesForSense(clusters, out, sense, senseName, debug);
		}
	}

	private void writeSimilaritiesForSense(Map<String, Map<Integer, List<String>>> clusters, BufferedWriter out,
			Entry<String, Map<Integer, List<String>>> sense, String senseName, boolean debug) throws IOException {
		for (Entry<Integer, List<String>> senseIdClusters : sense.getValue().entrySet()) {
			Integer senseId = senseIdClusters.getKey();
			List<String> clusterWords1 = senseIdClusters.getValue();
			for (String word : clusterWords1) {
				writeSimilarityForClusterWord(clusters, out, senseName, senseId, clusterWords1, word, debug);
			}
		}
	}

	private void writeSimilarityForClusterWord(Map<String, Map<Integer, List<String>>> clusters, BufferedWriter out,
			String senseName, Integer senseId, List<String> clusterWords1, String word, boolean debug)
					throws IOException {
		Map<Integer, List<String>> possibleSensesForClusterWord = clusters.get(word);
		if (possibleSensesForClusterWord == null) {
			// may be too verbose
			LOG.debug("No cluster for word {}", word);
			return;
		}
		for (Entry<Integer, List<String>> senseForWord : possibleSensesForClusterWord.entrySet()) {
			Integer wordSenseId = senseForWord.getKey();
			List<String> clusterWords2 = senseForWord.getValue();
			double similarity = computeSimilarity(clusterWords1, clusterWords2);
			if (similarity != 0)
				out.append(senseName + "#" + senseId + "\t" + word + "#" + wordSenseId + "\t" + similarity);
			if (debug) {
				appendSimilarWords(out, clusterWords1, clusterWords2);
			}
			out.append("/n");

		}
	}

	private void appendSimilarWords(BufferedWriter out, List<String> clusterWords1, List<String> clusterWords2)
			throws IOException {
		out.append("\t");
		for (String cw1 : clusterWords1) {
			if (clusterWords2.contains(cw1)) {
				out.append(cw1).append(", ");
			}
		}
	}

	private double computeSimilarity(List<String> clusterWords1, List<String> clusterWords2) {

		double commonWords = 0;
		for (int i = 0; i < clusterWords1.size(); i++) {
			String word = clusterWords1.get(i);
			// TODO weight words
			if (clusterWords2.contains(word)) {
				commonWords += 1;
			}
		}
		return commonWords / (clusterWords1.size() + clusterWords2.size());
	}
}
