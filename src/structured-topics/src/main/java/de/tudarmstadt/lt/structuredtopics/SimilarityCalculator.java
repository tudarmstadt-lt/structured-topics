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

	public void calculateSimilarities(Map<String, Map<Integer, List<Feature>>> clusters, File output, boolean debug) {
		try (BufferedWriter out = Utils.openWriter(output)) {
			writeSimilarities(clusters, out, debug);
		} catch (Exception e) {
			Throwables.propagate(e);
		}
	}

	private void writeSimilarities(Map<String, Map<Integer, List<Feature>>> clusters, BufferedWriter out, boolean debug)
			throws IOException {
		int count = 0;
		Stopwatch watch = Stopwatch.createStarted();
		Set<Entry<String, Map<Integer, List<Feature>>>> entrySet = clusters.entrySet();
		int total = entrySet.size();
		for (Entry<String, Map<Integer, List<Feature>>> sense : entrySet) {
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

	private void writeSimilaritiesForSense(Map<String, Map<Integer, List<Feature>>> clusters, BufferedWriter out,
			Entry<String, Map<Integer, List<Feature>>> sense, String senseName, boolean debug) throws IOException {
		for (Entry<Integer, List<Feature>> senseIdClusters : sense.getValue().entrySet()) {
			Integer senseId = senseIdClusters.getKey();
			List<Feature> clusterWords1 = senseIdClusters.getValue();
			for (Feature feature : clusterWords1) {
				writeSimilarityForClusterWord(clusters, out, senseName, senseId, clusterWords1, feature.getWord(),
						debug);
			}
		}
	}

	private void writeSimilarityForClusterWord(Map<String, Map<Integer, List<Feature>>> clusters, BufferedWriter out,
			String senseName, Integer senseId, List<Feature> clusterWords1, String word, boolean debug)
					throws IOException {
		Map<Integer, List<Feature>> possibleSensesForClusterWord = clusters.get(word);
		if (possibleSensesForClusterWord == null) {
			// false assumption, jo != bim. needs algorithm rework to compare
			// two clusters
			return;
		}
		for (Entry<Integer, List<Feature>> senseForWord : possibleSensesForClusterWord.entrySet()) {
			Integer wordSenseId = senseForWord.getKey();
			List<Feature> clusterWords2 = senseForWord.getValue();
			double similarity = computeSimilarity(clusterWords1, clusterWords2);
			if (similarity != 0)
				out.append(senseName + "#" + senseId + "\t" + word + "#" + wordSenseId + "\t" + similarity);
			if (debug) {
				appendSimilarWords(out, clusterWords1, clusterWords2);
			}
			out.append("/n");

		}
	}

	private void appendSimilarWords(BufferedWriter out, List<Feature> clusterWords1, List<Feature> clusterWords2)
			throws IOException {
		out.append("\t");
		for (Feature cw1 : clusterWords1) {
			if (clusterWords2.contains(cw1)) {
				out.append(cw1.getWord()).append(", ");
			}
		}
	}

	private double computeSimilarity(List<Feature> clusterWords1, List<Feature> clusterWords2) {

		double commonWeights = 0;
		for (int i = 0; i < clusterWords1.size(); i++) {
			Feature f = clusterWords1.get(i);
			String word = f.getWord();
			// TODO weight words
			for (Feature f2 : clusterWords2) {
				if (f2.getWord().equals(word)) {
					commonWeights += f.getWeight() * f.getWeight();
				}
			}
		}
		return commonWeights / (vectorLength(clusterWords1) + vectorLength(clusterWords2));
	}

	private double vectorLength(List<Feature> clusterWords) {
		double sum = 0;
		for (Feature f : clusterWords) {
			sum += (f.getWeight() * f.getWeight());
		}
		return Math.sqrt(sum);
	}

	// private double computeSimilarity(List<Feature> clusterWords1,
	// List<Feature> clusterWords2) {
	//
	// double commonWords = 0;
	// for (int i = 0; i < clusterWords1.size(); i++) {
	// String word = clusterWords1.get(i).getWord();
	// // TODO weight words
	// for (Feature f : clusterWords2) {
	// if (f.getWord().equals(word)) {
	// commonWords++;
	// break;
	// }
	// }
	// }
	// return commonWords / (clusterWords1.size() + clusterWords2.size());
	// }
}
