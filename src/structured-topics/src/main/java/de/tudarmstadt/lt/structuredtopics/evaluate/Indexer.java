package de.tudarmstadt.lt.structuredtopics.evaluate;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

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

	public static final String CLUSTER_LABEL_NONE = "<no_label>";
	private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

	public static void main(String[] args) {
		Options options = createOptions();
		Node node = null;
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			Builder settings = Settings.settingsBuilder().put("path.home", cl.getOptionValue(OPTION_INDEX_DIR));
			node = NodeBuilder.nodeBuilder().clusterName("structured-topics-el").settings(settings).node();
			File clusters = new File(cl.getOptionValue(OPTION_INPUT_CLUSTERS));
			File clusterLabelsFile = new File(cl.getOptionValue(OPTION_INPUT_LABELS));
			Map<Integer, String> clusterLabels = readClusterLabels(clusterLabelsFile);
			Client client = node.client();
			client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
			try (BufferedReader in = Utils.openReader(clusters, InputMode.GZ)) {
				String line = null;
				while ((line = in.readLine()) != null) {
					String[] split = line.split("\\t");
					if (split.length != 3) {
						LOG.error("Invalid cluster: {}", line);
					}
					Integer clusterId = Integer.valueOf(split[0]);
					Integer clusterSize = Integer.valueOf(split[1]);
					String[] clusterWords = split[2].split(",\\s*");
					String label = defaultString(clusterLabels.get(clusterId), CLUSTER_LABEL_NONE);
					IndexResponse response = client.prepareIndex(EL_INDEX, EL_INDEX_TYPE, clusterId.toString())
							.setSource(XContentFactory.jsonBuilder().startObject().field(EL_FIELD_CLUSTER_LABEL, label)
									.field(EL_FIELD_CLUSTER_WORDS, clusterWords).endObject())
							.get();
					LOG.info(response.toString());
				}
			}
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

	private static Map<Integer, String> readClusterLabels(File clusterLabelsFile) {
		Map<Integer, String> clusterLabels = Maps.newHashMap();
		try {
			List<String> lines = Files.readLines(clusterLabelsFile, Charset.defaultCharset());
			for (String line : lines) {
				String[] split = line.split("\t");
				if (split.length < 2) {
					continue;
				}
				Integer clusterId = Integer.valueOf(split[0]);
				String label = split[1];
				clusterLabels.put(clusterId, label);
			}
		} catch (IOException e) {
			LOG.error("Error while reading cluster labels", e);
		}
		return clusterLabels;
	}

	private static Options createOptions() {
		Options options = new Options();
		Option inputClusters = Option.builder(OPTION_INPUT_CLUSTERS).argName("sense clusters")
				.desc("Input file containing the clusters, in .gz-format").hasArg().required().build();
		options.addOption(inputClusters);
		Option inputLabels = Option.builder(OPTION_INPUT_LABELS).argName("cluster labels")
				.desc(".csv file, containing a cluster id and a label, separated by tab").hasArg().required().build();
		options.addOption(inputLabels);
		Option indexDir = Option.builder(OPTION_INDEX_DIR).argName("index directory")
				.desc("path to directory, where the elasticsearch-index will be created").hasArg().required().build();
		options.addOption(indexDir);
		return options;
	}

}
