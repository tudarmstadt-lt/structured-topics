package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.BufferedReader;
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
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class Indexer {

	public static final String EL_FIELD_CLUSTER_LABEL = "label";
	public static final String EL_FIELD_CLUSTER_WORDS = "words";
	public static final String EL_INDEX_TYPE = "cluster";
	public static final String EL_INDEX = "topics";

	private static final String OPTION_INPUT_CLUSTERS = "clusters";
	private static final String OPTION_INPUT_LABELS = "labels";
	private static final String OPTION_INDEX_DIR = "index";

	private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

	public static void main(String[] args) {
		Options options = createOptions();
		Node node = null;
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File clusters = new File(cl.getOptionValue(OPTION_INPUT_CLUSTERS));
			File clusterLabelsFile = new File(cl.getOptionValue(OPTION_INPUT_LABELS));
			Map<String, String> clusterLabels = readWordLabels(clusterLabelsFile);
			Builder settings = Settings.settingsBuilder().put("path.home", cl.getOptionValue(OPTION_INDEX_DIR));
			node = NodeBuilder.nodeBuilder().clusterName("structured-topics-el").settings(settings).node();
			Client client = node.client();
			client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
			buildIndex(clusters, clusterLabels, client);
		} catch (ParseException e) {
			LOG.error("Invalid arguments: {}", e.getMessage());
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("Error", e);
		} finally {
			if (node != null) {
				node.close();
			}
		}
	}

	private static void buildIndex(File clusters, Map<String, String> wordLabels, Client client) throws IOException {
		try (BufferedReader in = Utils.openReader(clusters, InputMode.GZ)) {
			String line = null;
			while ((line = in.readLine()) != null) {
				String[] split = line.split("\\t");
				if (split.length != 3) {
					LOG.error("Invalid cluster: {}", line);
				}
				Integer clusterId = Integer.valueOf(split[0]);
				Integer clusterSize = Integer.valueOf(split[1]);
				String lowerCase = split[2].toLowerCase();
				String[] clusterWords = lowerCase.split(",\\s*");
				String[] labels = getLabels(clusterWords, wordLabels);
				removePostTagAndSenseId(clusterWords);
				if (labels.length > 0) {
					// clusters without labels are skipped
					IndexResponse response = client.prepareIndex(EL_INDEX, EL_INDEX_TYPE)
							.setSource(XContentFactory.jsonBuilder().startObject()
									.field(EL_FIELD_CLUSTER_WORDS, clusterWords).field(EL_FIELD_CLUSTER_LABEL, labels)
									.endObject())
							.get();
					LOG.info(response.toString());
				}
			}
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

	private static void removePostTagAndSenseId(String[] clusterWords) {
		for (int i = 0; i < clusterWords.length; i++) {
			String fullWord = clusterWords[i];
			int firstHashIndex = fullWord.indexOf("#");
			if (firstHashIndex != -1) {
				String word = fullWord.substring(0, firstHashIndex);
				clusterWords[i] = word;
			}
		}

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
				.desc(".csv file, containing a word#postag#senseid, eg. java#NN#1 and any label, separated by tab")
				.hasArg().required().build();
		options.addOption(inputLabels);
		Option indexDir = Option.builder(OPTION_INDEX_DIR).argName("index directory")
				.desc("path to directory, where the elasticsearch-index will be created").hasArg().required().build();
		options.addOption(indexDir);
		return options;
	}

}
