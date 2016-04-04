package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import de.tudarmstadt.lt.structuredtopics.Utils;

public class Crawler {
	private CachingApi api;
	private long lastTimeSaved = 0;

	private static final Logger LOG = LoggerFactory.getLogger(Crawler.class);

	private static final String OPTION_API_KEY = "key";
	private static final String OPTION_STARTING_SYNSETS = "synsetStart";
	private static final String OPTION_MAX_STEPS = "steps";
	private static final String OPTION_FOUND_SENSES_FILE = "out";
	private static final String OPTION_VISITED_SYNSETS_FILE = "visited";
	private static final String OPTION_QUEUE = "queue";
	private static final String OPTION_CLEAN_SENSES = "cleanSenses";

	private static final String OPTION_API_CACHE = "apiCache";
	private static final long SAVE_EACH_MS = TimeUnit.SECONDS.toMillis(30);

	public Crawler(File cacheLocation, String apiKey) {
		this.api = new RetryCallCachingApi(cacheLocation, apiKey);
	}

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File cacheLocation = new File(cl.getOptionValue(OPTION_API_CACHE));
			Crawler crawler = new Crawler(cacheLocation, cl.getOptionValue(OPTION_API_KEY));
			Set<String> startingSynsets = Sets.newHashSet(cl.getOptionValue(OPTION_STARTING_SYNSETS).split(","));
			int maxSteps = Integer.valueOf(cl.getOptionValue(OPTION_MAX_STEPS));
			File out = new File(cl.getOptionValue(OPTION_FOUND_SENSES_FILE));
			File queue = new File(cl.getOptionValue(OPTION_QUEUE));
			File visited = new File(cl.getOptionValue(OPTION_VISITED_SYNSETS_FILE));
			boolean cleanSenses = cl.hasOption(OPTION_CLEAN_SENSES);
			LOG.info("Crawling synsets {} with maxSteps {}", startingSynsets, maxSteps);
			crawler.crawl(startingSynsets, maxSteps, out, queue, visited, cleanSenses);
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
		Option labeledClusters = Option.builder(OPTION_STARTING_SYNSETS).argName("starting synsets")
				.desc("csv list of ids of the synsets to start with, e.g. 'bn:00048043n,bn:00004625n'").hasArg()
				.build();
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
		Option apiCache = Option.builder(OPTION_API_CACHE).argName("apiCache")
				.desc("Folder where api calls will be cached").required().hasArg().build();
		options.addOption(apiCache);
		Option cleanSenses = Option.builder(OPTION_CLEAN_SENSES).argName("clean senses")
				.desc("(optional), if set, this will clean senses like <sense>#tag#id and <sense>_(domain) to <sense>. "
						+ "More specific: this will remove everything after the second last '#' and the last '_(' (each inclusive)")
				.build();
		options.addOption(cleanSenses);
		return options;
	}

	@SuppressWarnings("rawtypes")
	private void crawl(Set<String> startingSynsetsId, int maxSteps, File outFile, File queueFile, File visitedFile,
			boolean cleanSenses) {
		Set<String> visitedSynsets = Utils.loadUniqueLines(visitedFile);
		LinkedHashSet<String> queue = Sets.newLinkedHashSet(Utils.loadUniqueLines(queueFile));
		queue.addAll(startingSynsetsId);
		Set<String> foundDomains = Sets.newHashSet();
		int step = 0;
		int countFoundSenses = 0;
		String currentSynsetId = null;
		try (BufferedWriter out = new BufferedWriter(new FileWriter(outFile, true))) {
			while (!queue.isEmpty() && step < maxSteps) {
				Iterator<String> it = queue.iterator();
				currentSynsetId = it.next();
				it.remove();
				if (!visitedSynsets.add(currentSynsetId)) {
					LOG.info("Skipping {}, already visited", currentSynsetId);
				} else {
					// only count a step if a new synset will be expanded
					step++;
					String synset = api.getSynset(currentSynsetId);
					if (StringUtils.isEmpty(synset)) {
						LOG.error("Empty response from api for synset {}, putting it at end of queue", currentSynsetId);
						visitedSynsets.remove(currentSynsetId);
						queue.add(currentSynsetId);
						continue;
					}
					Gson gson = new GsonBuilder().create();
					Map json = (Map) gson.fromJson(synset, Object.class);
					String mainSense = (String) json.get("mainSense");
					if (mainSense == null) {
						LOG.error("Empty main sense in synset {}", currentSynsetId);
						continue;
					} else {
						Map domains = (Map) json.get("domains");
						if (cleanSenses) {
							mainSense = clean(mainSense);
						}
						for (Object domain : domains.keySet()) {
							foundDomains.add((String) domain);
							Object domainWeight = domains.get(domain);
							String senseWeight = mainSense + "\t" + domainWeight + "\t" + domain + "\t"
									+ currentSynsetId;
							LOG.info("Adding {}", senseWeight);
							out.write(senseWeight + "\n");
							out.flush();
							countFoundSenses++;
						}
					}
					// found a sense from domain, expand synset
					String edgesJson = api.getEdges(currentSynsetId);
					if (StringUtils.isEmpty(edgesJson)) {
						LOG.error("Failed to expand synset {}", currentSynsetId);
						continue;
					}
					List edges = (List) gson.fromJson(edgesJson, Object.class);
					for (Object edge : edges) {
						Map edgeData = (Map) edge;
						String targetSynset = (String) edgeData.get("target");
						String language = (String) edgeData.get("language");
						// TODO all languages?
						if (language.equals("EN")) {
							queue.add(targetSynset);
						}
					}

				}
				if (System.currentTimeMillis() - lastTimeSaved > SAVE_EACH_MS) {
					saveQueueAndVisited(queueFile, visitedFile, visitedSynsets, queue);
					lastTimeSaved = System.currentTimeMillis();
				}
			}
		} catch (Exception e) {
			LOG.error("Error while crawling, {} will be visited again", currentSynsetId);
			LOG.error("Error: ", e);
			queue.add(currentSynsetId);
			visitedSynsets.remove(currentSynsetId);
		} finally {
			LOG.info("Done crawling, found {} new senses with {} calls to the api. Remaining queue size: {}",
					countFoundSenses, api.getApiCallCount(), queue.size());
			saveQueueAndVisited(queueFile, visitedFile, visitedSynsets, queue);
			LOG.info("Finished");
		}
	}

	protected static String clean(String mainSense) {
		// <sense>#tag#id
		int lastHash = mainSense.lastIndexOf("#");
		int secondLastHash = mainSense.lastIndexOf("#", lastHash - 1);
		// <sense>_(domain)
		int domainStart = mainSense.lastIndexOf("_(");
		int cleanIndex = Math.max(secondLastHash, domainStart);
		// at least one matched, prune as little as possible
		if (cleanIndex != -1) {
			return mainSense.substring(0, cleanIndex);
		}
		return mainSense;
	}

	private void saveQueueAndVisited(File queueFile, File visitedFile, Set<String> visitedSynsets,
			LinkedHashSet<String> queue) {
		LOG.info("Saving queue");
		saveLines(queue, queueFile);
		LOG.info("Saving visited synsets");
		saveLines(visitedSynsets, visitedFile);
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

}
