package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class ClusterLabelLookup {

	private static final String OPTION_INPUT_CLUSTERS = "clusters";
	private static final String OPTION_INPUT_LABELS = "labels";
	private static final String OPTION_LABELED_CLUSTERS = "labeledClustersOut";

	private static final Logger LOG = LoggerFactory.getLogger(ClusterLabelLookup.class);

	// TODO first approach for automated labeling of clusters
	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File clusters = new File(cl.getOptionValue(OPTION_INPUT_CLUSTERS));
			File labeledClustersOutFile = new File(cl.getOptionValue(OPTION_LABELED_CLUSTERS));
			Map<String, String> clusterLabels = Maps.newHashMap();
			if (cl.hasOption(OPTION_INPUT_LABELS)) {
				File clusterLabelsFile = new File(cl.getOptionValue(OPTION_INPUT_LABELS));
				LOG.info("Labels provided: {}", clusterLabelsFile.getAbsolutePath());
				clusterLabels = readWordLabels(clusterLabelsFile);
			} else {
				LOG.info("No labels found, all labels will be blank");
			}
			labelClusters(clusters, clusterLabels, labeledClustersOutFile);
		} catch (ParseException e) {
			LOG.error("Invalid arguments: {}", e.getMessage());
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("Error", e);
		}
	}

	private static void labelClusters(File clusters, Map<String, String> wordLabels, File labeledClustersOutFile)
			throws IOException {
		try (BufferedWriter out = Utils.openGzipWriter(labeledClustersOutFile)) {
			try (BufferedReader in = Utils.openReader(clusters, InputMode.GZ)) {
				String line = null;
				while ((line = in.readLine()) != null) {
					String[] split = line.split("\\t");
					if (split.length != 3) {
						LOG.error("Invalid cluster: {}", line);
					}
					Integer clusterId = Integer.valueOf(split[0]);
					Integer clusterSize = Integer.valueOf(split[1]);
					String clusterWordsPlain = split[2].toLowerCase();
					String[] clusterWords = clusterWordsPlain.split(",\\s*");
					String[] labels = getLabels(clusterWords, wordLabels);
					writeLabeledCluster(out, clusterId, clusterSize, labels, clusterWordsPlain);
				}
			}
		}
	}

	private static void writeLabeledCluster(BufferedWriter out, Integer clusterId, Integer clusterSize, String[] labels,
			String clusterWordsPlain) {
		StringBuilder b = new StringBuilder();
		b.append(clusterId).append("\t").append(clusterSize).append("\t");
		for (String label : labels) {
			b.append(label).append(", ");
		}
		b.append("\t").append(clusterWordsPlain).append("\n");
		try {
			out.write(b.toString());
		} catch (IOException e) {
			LOG.error("Writing cluster with id {} failed", clusterId, e);
		}
	}

	private static String[] getLabels(String[] words, Map<String, String> wordLabels) {
		Set<String> labels = Sets.newHashSet();
		for (String word : words) {
			String label = wordLabels.get(word);
			if (label != null) {
				labels.add(label);
			}
		}
		return labels.toArray(new String[0]);
	}

	private static Map<String, String> readWordLabels(File labelsFile) {
		Map<String, String> wordLabels = Maps.newHashMap();
		try {
			List<String> lines = Files.readLines(labelsFile, Charset.defaultCharset());
			for (String line : lines) {
				String[] split = line.toLowerCase().split("\t");
				if (split.length < 2) {
					continue;
				}
				String word = split[0];
				String label = split[1];
				wordLabels.put(word, label);
			}
		} catch (IOException e) {
			LOG.error("Error while reading labels", e);
		}
		return wordLabels;
	}

	private static Options createOptions() {
		Options options = new Options();
		Option inputClusters = Option.builder(OPTION_INPUT_CLUSTERS).argName("sense clusters")
				.desc("Input file containing the clusters, in .gz-format").hasArg().required().build();
		options.addOption(inputClusters);
		Option inputLabels = Option.builder(OPTION_INPUT_LABELS).argName("cluster labels")
				.desc("(optional) .csv file, containing a word#postag#senseid, eg. java#NN#1 and any label, separated by tab")
				.hasArg().build();
		options.addOption(inputLabels);
		Option labeledClusters = Option.builder(OPTION_LABELED_CLUSTERS).argName("clusters with labels")
				.desc("new file with the same content as the clusters, but with an additional column for the labels of each cluster. Will be a csv.gz format, same as the clusters")
				.hasArg().required().build();
		options.addOption(labeledClusters);
		return options;
	}
}
