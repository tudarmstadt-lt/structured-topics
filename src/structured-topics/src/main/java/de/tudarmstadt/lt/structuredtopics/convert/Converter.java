package de.tudarmstadt.lt.structuredtopics.convert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.lt.structuredtopics.Feature;
import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class Converter {

	private static final Logger LOG = LoggerFactory.getLogger(Converter.class);

	/*
	 * first arg: input-file (DDT csv in gz format) second arg: output-file-path
	 */
	public static void main(String[] args) {
		Parser parser = new Parser();
		File input = new File(args[0]);
		LocalTime now = LocalTime.now();
		File output = new File(args[1], input.getName() + now.getHour() + "_" + now.getMinute() + "_" + now.getSecond()
				+ "-wordFeatureCounts.gz");
		File output2 = new File(args[1],
				input.getName() + now.getHour() + "_" + now.getMinute() + "_" + now.getSecond() + "-wordCounts.gz");
		LOG.info("Input: {}\n Output: {}", input.getAbsolutePath(), output.getAbsolutePath());
		Map<String, Map<Integer, List<Feature>>> readClusters = parser.readClusters(input, InputMode.GZ);
		int total = readClusters.size();
		int count = 0;
		LOG.info("Starting conversion");
		try (BufferedWriter out = Utils.openWriter(output)) {
			for (Entry<String, Map<Integer, List<Feature>>> cluster : readClusters.entrySet()) {
				if (count++ % 10000 == 0) {
					LOG.info("Progress: {}/{}", count, total);
				}
				String senseWord = cluster.getKey();
				Map<Integer, List<Feature>> senses = cluster.getValue();
				for (Entry<Integer, List<Feature>> sense : senses.entrySet()) {
					Integer senseId = sense.getKey();
					for (Feature feature : sense.getValue()) {
						out.append(senseWord + "#" + senseId + "\t" + feature.getWord() + "\t" + 1);
						out.append("\n");
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try (BufferedWriter out = Utils.openWriter(output2)) {
			for (Entry<String, Map<Integer, List<Feature>>> cluster : readClusters.entrySet()) {
				if (count++ % 10000 == 0) {
					LOG.info("Progress: {}/{}", count, total);
				}
				String senseWord = cluster.getKey();
				Map<Integer, List<Feature>> senses = cluster.getValue();
				for (Entry<Integer, List<Feature>> sense : senses.entrySet()) {
					Integer senseId = sense.getKey();
					out.append(senseWord + "#" + senseId + "\t" + 1);
					out.append("\n");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOG.info("Finished, results available at {} and {}", output.getAbsolutePath(), output2.getAbsolutePath());
	}
}
