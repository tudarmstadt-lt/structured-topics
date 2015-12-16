package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

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
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.collect.Sets;

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
			Client client = node.client();
			File data = new File(cl.getOptionValue(OPTION_INPUT_NER_XML));
			File outFile = new File(cl.getOptionValue(OPTION_OUTPUT_FILE));
			client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
			labelClusters(data, outFile, client);
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

	private static void labelClusters(File data, File outFile, Client client)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(data);

		try (BufferedWriter out = new BufferedWriter(new FileWriter(outFile))) {
			NodeList elements = doc.getElementsByTagName("*");
			for (int i = 0; i < elements.getLength(); i++) {
				org.w3c.dom.Node item = elements.item(i);
				if (item.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
					String nodeName = item.getNodeName();
					boolean isNamedEntity = nodeName.equals("NamedEntityInText");
					boolean istSimpleText = nodeName.equals("SimpleTextPart");
					if (isNamedEntity || istSimpleText) {
						String text = item.getTextContent().toLowerCase();
						try {
							String[] words = text.split("\\s++");
							for (String word : words) {
								BoolQueryBuilder query = QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery())
										.filter(QueryBuilders.termQuery(Indexer.EL_FIELD_CLUSTER_WORDS, word))
										.minimumNumberShouldMatch(1);
								SearchResponse searchResponse = client.prepareSearch(Indexer.EL_INDEX)
										.setTypes(Indexer.EL_INDEX_TYPE).setSearchType(SearchType.QUERY_AND_FETCH)
										.setQuery(query).setFrom(0).setSize(20).setExplain(true).execute().actionGet();
								SearchHits hits = searchResponse.getHits();
								if (isNamedEntity) {
									out.write(word + "\t NE \t");
									// search labels only for named entities
									Set<String> labels = Sets.newHashSet();
									for (SearchHit hit : hits) {
										GetResponse response = client
												.prepareGet(Indexer.EL_INDEX, Indexer.EL_INDEX_TYPE, hit.getId())
												.setFields(Indexer.EL_FIELD_CLUSTER_LABEL).execute().actionGet();
										List<Object> labelsResponse = response.getField(Indexer.EL_FIELD_CLUSTER_LABEL)
												.getValues();
										for (Object label : labelsResponse) {
											labels.add((String) label);
										}
									}
									for (Object label : labels) {
										out.write(label + ", ");
									}
								} else {
									out.write(word + "\t\t");
								}
								out.write("\n");
							}
						} catch (Exception e) {
							e.printStackTrace();
							LOG.error("Error while searching for node with text: {}", text, e);
						}
					}
				}
			}

		}
	}

	private static Options createOptions() {
		Options options = new Options();
		Option inputClusters = Option.builder(OPTION_INPUT_NER_XML).argName("NER XML")
				.desc("XML-file from the n3-collection").hasArg().required().build();
		options.addOption(inputClusters);
		Option inputLabels = Option.builder(OPTION_OUTPUT_FILE).argName("output file")
				.desc("results as .csv file containing all named entities with their associated labels").hasArg()
				.required().build();
		options.addOption(inputLabels);
		Option indexDir = Option.builder(OPTION_INDEX_DIR).argName("index directory")
				.desc("path to directory, where the elasticsearch-index is located").hasArg().required().build();
		options.addOption(indexDir);
		return options;
	}

}
