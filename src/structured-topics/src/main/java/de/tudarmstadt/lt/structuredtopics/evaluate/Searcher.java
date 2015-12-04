package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class Searcher {

	private static final String OPTION_INDEX_DIR = "index";
	private static final String OPTION_INPUT_NER_XML = "data";
	private static final String OPTION_OUTPUT_FILE = "out";

	private static final Logger LOG = LoggerFactory.getLogger(Searcher.class);

	public static void main(String[] args) {
		Options options = createOptions();
		Node node = null;
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			Builder settings = Settings.settingsBuilder().put("path.home", cl.getOptionValue(OPTION_INDEX_DIR));
			node = NodeBuilder.nodeBuilder().clusterName("structured-topics-el").settings(settings).node();
			File data = new File(cl.getOptionValue(OPTION_INPUT_NER_XML));
			File outFile = new File(cl.getOptionValue(OPTION_OUTPUT_FILE));
			Client client = node.client();
			client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(data);

			try (BufferedWriter out = new BufferedWriter(new FileWriter(outFile))) {
				NodeList elements = doc.getElementsByTagName("*");
				for (int i = 0; i < elements.getLength(); i++) {
					org.w3c.dom.Node item = elements.item(i);
					if (item.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
						String nodeName = item.getNodeName();
						if (nodeName.equals("TextWithNamedEntities") || nodeName.equals("NamedEntityInText")) {
							String text = item.getTextContent();
							try {
								String[] words = text.split("\\s+");

								for (String word : words) {
									SearchResponse searchResponse = client.prepareSearch(Indexer.EL_INDEX)
											.setTypes(Indexer.EL_INDEX_TYPE).setSearchType(SearchType.QUERY_AND_FETCH)
											.setQuery(QueryBuilders.termQuery(Indexer.EL_FIELD_CLUSTER_WORDS, word))
											.setFrom(0).setSize(3).execute().actionGet();
									out.write(word + "\t");
									for (SearchHit hit : searchResponse.getHits()) {
										GetResponse response = client
												.prepareGet(Indexer.EL_INDEX, Indexer.EL_INDEX_TYPE, hit.getId())
												.setFields(Indexer.EL_FIELD_CLUSTER_LABEL).execute().actionGet();
										String label = (String) response.getField(Indexer.EL_FIELD_CLUSTER_LABEL)
												.getValue();
										if (Indexer.CLUSTER_LABEL_NONE.equals("label")) {
											label = "-";
										}
										out.write(label + ", ");
									}
									out.write("\n");
								}
							} catch (Exception e) {
								LOG.error("Error while searching for node with text: {}", text, e);
							}
						}
					}
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

	private static Options createOptions() {
		Options options = new Options();
		Option inputClusters = Option.builder(OPTION_INPUT_NER_XML).argName("NER XML")
				.desc("XML-file from the n3-collection").hasArg().required().build();
		options.addOption(inputClusters);
		Option inputLabels = Option.builder(OPTION_OUTPUT_FILE).argName("cluster labels")
				.desc(".csv file, containing a cluster id and a label, separated by tab").hasArg().required().build();
		options.addOption(inputLabels);
		Option indexDir = Option.builder(OPTION_INDEX_DIR).argName("index directory")
				.desc("path to directory, where the elasticsearch-index is located").hasArg().required().build();
		options.addOption(indexDir);
		return options;
	}

}
