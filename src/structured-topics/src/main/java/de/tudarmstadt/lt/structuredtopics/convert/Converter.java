package de.tudarmstadt.lt.structuredtopics.convert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class Converter {

	private static final Logger LOG = LoggerFactory.getLogger(Converter.class);

	/*
	 * first arg: input-file (DDT csv in gz format) second arg:
	 * output-file-prefix (will be a gz csv)
	 */
	public static void main(String[] args) {
		Parser parser = new Parser();
		File input = new File(args[0]);
		File output = new File(input.getName() + "_" + args[1]);
		LOG.info("Input: {}\n Output: {}", input.getAbsolutePath(), output.getAbsolutePath());
		Map<String, Map<Integer, List<String>>> readClusters = parser.readClusters(input, InputMode.GZ);
		int total = readClusters.size();
		int count = 0;
		LOG.info("Starting conversion");
		try (BufferedWriter out = Utils.openWriter(output)) {
			for (Entry<String, Map<Integer, List<String>>> cluster : readClusters.entrySet()) {
				if (count++ % 10000 == 0) {
					LOG.info("Progress: {}/{}", count, total);
				}
				String senseWord = cluster.getKey();
				Map<Integer, List<String>> senses = cluster.getValue();
				for (Entry<Integer, List<String>> sense : senses.entrySet()) {
					for (String clusterWord : sense.getValue()) {
						out.append(senseWord + "\\t" + clusterWord + "\\t" + 1);
						out.append("\n");
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		LOG.info("Finished, results available at {}", output.getAbsolutePath());
	}
}
