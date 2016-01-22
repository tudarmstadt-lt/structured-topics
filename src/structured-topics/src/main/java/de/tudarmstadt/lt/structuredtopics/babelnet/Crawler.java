package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Crawler {
	private String apiKey;
	private static final String API = "babelnet.io/v2/";
	private static final String API_GET_SYNSET = "getSynset";
	private static final String API_GET_EDGES = "getEdges";
	private static final Logger LOG = LoggerFactory.getLogger(Crawler.class);
	private static final int MS_BETWEEN_REQUEST = 500;

	private static final String OPTION_API_KEY = "key";
	private static final String OPTION_STARTING_SYNSET = "synsetStart";
	private static final String OPTION_DOMAIN = "domain";
	private static final String OPTION_MAX_STEPS = "steps";
	private static final String OPTION_FOUND_SENSES_FILE = "out";
	private static final String OPTION_VISITED_SYNSETS_FILE = "visited";
	private static final String OPTION_QUEUE = "queue";

	public Crawler(String apiKey) {
		this.apiKey = apiKey;
	}

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			Crawler crawler = new Crawler(cl.getOptionValue(OPTION_API_KEY));
			String startingSynset = cl.getOptionValue(OPTION_STARTING_SYNSET);
			String domain = cl.getOptionValue(OPTION_DOMAIN);
			int maxSteps = Integer.valueOf(cl.getOptionValue(OPTION_MAX_STEPS));
			File out = new File(cl.getOptionValue(OPTION_FOUND_SENSES_FILE));
			File queue = new File(cl.getOptionValue(OPTION_QUEUE));
			File visited = new File(cl.getOptionValue(OPTION_VISITED_SYNSETS_FILE));
			LOG.info("Crawling synset {} for domain {} with maxSteps {}", startingSynset, domain, maxSteps);
			crawler.crawl(startingSynset, domain, maxSteps, out, queue, visited);
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

	private static Options createOptions() {
		Options options = new Options();
		Option apiKey = Option.builder(OPTION_API_KEY).argName("api key").desc("The api key").hasArg().required()
				.build();
		options.addOption(apiKey);
		Option domain = Option.builder(OPTION_DOMAIN).argName("domain")
				.desc("the domain to aggregate, e.g. 'COMPUTING'").required().hasArg().build();
		options.addOption(domain);
		Option labeledClusters = Option.builder(OPTION_STARTING_SYNSET).argName("starting synset")
				.desc("Id of the synset to start with, e.g. 'bn:00048043n'").hasArg().build();
		options.addOption(labeledClusters);
		Option steps = Option.builder(OPTION_MAX_STEPS).argName("max steps")
				.desc("Number of steps to perform. Each step will expand one synset (up to 2 api-calls)").hasArg()
				.required().build();
		options.addOption(steps);
		Option out = Option.builder(OPTION_FOUND_SENSES_FILE).argName("out")
				.desc("File where the found senses will be appended (one per line)").hasArg().required().build();
		options.addOption(out);
		Option visited = Option.builder(OPTION_VISITED_SYNSETS_FILE).argName("visited")
				.desc("File for caching visited synsets, will be created if not exists").required().hasArg().build();
		options.addOption(visited);
		Option queue = Option.builder(OPTION_QUEUE).argName("queue")
				.desc("File where the remaining queue is stored after finishing, will be created if not exists")
				.required().hasArg().build();
		options.addOption(queue);
		return options;
	}

	@SuppressWarnings("rawtypes")
	private void crawl(String startingSynsetId, String domain, int maxSteps, File outFile, File queueFile,
			File visitedFile) {
		Set<String> visitedSynsets = loadLines(visitedFile);
		LinkedHashSet<String> queue = Sets.newLinkedHashSet(loadLines(queueFile));
		queue.add(startingSynsetId);
		int step = 0;
		int countFoundSenses = 0;
		try (BufferedWriter out = new BufferedWriter(new FileWriter(outFile, true))) {
			while (!queue.isEmpty() && step < maxSteps) {
				Iterator<String> it = queue.iterator();
				String currentSynset = it.next();
				it.remove();
				if (!visitedSynsets.add(currentSynset)) {
					LOG.info("Skipping {}, already visited", currentSynset);
				} else {
					// only count a step if a new synset will be expanded
					step++;
					try (CloseableHttpClient client = newClosableHttpClient()) {
						// wait between requests
						Thread.sleep(MS_BETWEEN_REQUEST);
						URIBuilder getSynsetBuilder = new URIBuilder().setScheme("http").setHost(API)
								.setPath(API_GET_SYNSET).addParameter("id", currentSynset).addParameter("key", apiKey);
						URI getSynsetUri = getSynsetBuilder.build();
						HttpGet get = new HttpGet(getSynsetUri);
						get.addHeader("Accept-Encoding", "gzip");
						HttpResponse response = client.execute(get);
						if (response.getStatusLine().getStatusCode() == 400) {
							LOG.error("Bad request: {}", response.toString());
							continue;
						}
						Gson gson = new GsonBuilder().create();
						String responseToString = responseToString(response);
						LOG.info("Accessing {}\nresponse:\n{}", getSynsetUri.toString(),
								StringUtils.abbreviate(responseToString, 500));
						Map json = (Map) gson.fromJson(responseToString, Object.class);
						Object message = json.get("message");
						if (message instanceof String && ((String) message)
								.contains("Your key is not valid or the daily requests limit has been reached")) {
							LOG.warn("Key invalid or limit reached");
							visitedSynsets.remove(currentSynset);
							break;
						}
						String mainSense = (String) json.get("mainSense");
						Map domains = (Map) json.get("domains");
						if (!domains.containsKey(domain)) {
							LOG.info("Skipping {} from synset {}, not in domain {}", mainSense, currentSynset, domain);
						} else {
							LOG.info("Adding {}", mainSense);
							out.write(mainSense + "\n");
							countFoundSenses++;
							// found a sense from domain, expand synset
							URIBuilder getEdgesBuilder = new URIBuilder().setScheme("http").setHost(API)
									.setPath(API_GET_EDGES).addParameter("id", currentSynset)
									.addParameter("key", apiKey);
							URI getEdgesUri = getEdgesBuilder.build();
							HttpGet get2 = new HttpGet(getEdgesUri);
							get.addHeader("Accept-Encoding", "gzip");
							HttpResponse response2 = client.execute(get2);
							if (response2.getStatusLine().getStatusCode() == 400) {
								LOG.error("Bad request: {}", response.toString());
								continue;
							}
							List edgesJson = (List) gson.fromJson(responseToString(response2), Object.class);
							for (Object edge : edgesJson) {
								Map edgeData = (Map) edge;
								String targetSynset = (String) edgeData.get("target");
								String language = (String) edgeData.get("language");
								// TODO all languages?
								if (language.equals("EN")) {
									queue.add(targetSynset);
								}
							}
						}

					} catch (Exception e) {
						LOG.error("Error while crawling", e);
						visitedSynsets.remove(currentSynset);
					}
				}
			}
		} catch (Exception e) {
			LOG.error("Error while crawling", e);
		} finally {
			LOG.info("Done crawling, found {} new senses", countFoundSenses);
			LOG.info("Saving queue");
			saveLines(queue, queueFile);
			LOG.info("Saving visited synsets");
			saveLines(visitedSynsets, visitedFile);
			LOG.info("Finished");
		}

	}

	private void saveLines(Set<String> set, File file) {
		LOG.info("Saving {} elements to {}", set.size(), file.getAbsolutePath());
		if (file.exists()) {
			file.delete();
			LOG.info("File exists, overwriting");
		}
		try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
			for (String s : set) {
				out.write(s + "\n");
			}
		} catch (IOException e) {
			LOG.error("Error while saving", e);
		}
	}

	private Set<String> loadLines(File file) {
		Set<String> set = Sets.newHashSet();
		if (file.exists()) {
			try (BufferedReader in = new BufferedReader(new FileReader(file))) {
				String line = null;
				while ((line = in.readLine()) != null) {
					set.add(line);
				}
			} catch (Exception e) {
				LOG.error("Error while reading {}", file, e);
			}
			LOG.info("Loaded {} lines from {}", set.size(), file.getAbsolutePath());
		} else {
			LOG.info("{} does not exist, using empty set", file.getAbsolutePath());
		}
		return set;
	}

	private static String responseToString(HttpResponse response) throws UnsupportedOperationException, IOException {
		try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
			StringBuffer resultString = new StringBuffer();
			String line = null;
			while ((line = rd.readLine()) != null) {
				resultString.append(line);
			}
			return resultString.toString();
		}
	}

	private static CloseableHttpClient newClosableHttpClient() {
		return HttpClientBuilder.create().setDefaultRequestConfig(
				RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(10000).build()).build();
	}
}
