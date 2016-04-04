package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Utils;

/**
 * Rates clusters of words to babelnet senses. The clusters should be in a
 * csv.gz file (tab separated). The first column is expected to contain the
 * clusterid, the third column is expected to contain the words of the cluster
 * (comma-separated). The output will be a tab-separated .csv file, containing
 * the clusterid in the first column, the top domains with each score in the
 * second column (domain|score, domain|score), scores for each found domain in
 * the third column (domain|simpleScore|cosineScore, domain|simpleScore...) and
 * all cluster labels in the 4. column (same as from the input format).
 *
 */
public class MapClustersToBabelnetSenses {

	private static final Logger LOG = LoggerFactory.getLogger(MapClustersToBabelnetSenses.class);

	private static final String OPTION_CLUSTERS_FILE = "clusters";
	private static final String OPTION_SENSES_FILE = "bnetSenses";
	private static final String OPTION_OUTPUT_FILE = "out";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File clusters = new File(cl.getOptionValue(OPTION_CLUSTERS_FILE));
			LOG.info("Loading babelnet senses");
			File sensesFile = new File(cl.getOptionValue(OPTION_SENSES_FILE));
			Set<String> sensesLines = Utils.loadUniqueLines(sensesFile);
			LOG.info("building index");
			Map<String, Map<String, Double>> index = buildIndexFromSenses(sensesLines);
			LOG.info("Scoring clusters");
			File out = new File(cl.getOptionValue(OPTION_OUTPUT_FILE));
			scoreAndWriteClusters(clusters, index, out);
			LOG.info("Done, results available at {}", out.getAbsolutePath());
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

	// domain - (sense - weight)
	private static Map<String, Map<String, Double>> buildIndexFromSenses(Set<String> sensesLines) {
		Map<String, Map<String, Double>> domains = Maps.newHashMap();
		for (String line : sensesLines) {
			String[] split = line.split("\t");
			if (split.length > 3) {
				String domain = split[2];
				Map<String, Double> domainSenses = domains.get(domain);
				if (domainSenses == null) {
					LOG.info("Adding index for domain {}", domain);
					domainSenses = Maps.newHashMap();
					domains.put(domain, domainSenses);
				}
				String sense = split[0];
				Double weight = Double.valueOf(split[1]);
				// TODO negative domain weight on babelnet, how to handle?
				if (weight < 0) {
					weight = 0.01;
				} else if (weight > 1) {
					weight = 1.0;
				}
				domainSenses.put(sense.toLowerCase(), weight);
				// words are separated by "_", so tokenize the sense to words
				// and add them too
				String[] senseWords = sense.split("_");
				for (String s : senseWords) {
					domainSenses.put(s.toLowerCase(), weight);
				}
			}
		}
		return domains;
	}

	private static void scoreAndWriteClusters(File clusters, Map<String, Map<String, Double>> index, File outFile) {
		DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		df.setMaximumFractionDigits(340);
		try (BufferedWriter out = Utils.openGzipWriter(outFile)) {
			List<String> lines = readLines(clusters);
			int size = lines.size();
			AtomicInteger count = new AtomicInteger();
			CountDownLatch latch = new CountDownLatch(lines.size());
			lines.parallelStream().forEach(line -> {
				try {
					if (count.incrementAndGet() % 100 == 0) {
						LOG.info("Progress line {}/{}", count.get(), size);
					}
					String[] split = line.split("\t");
					int clusterIndex = Integer.parseInt(split[0]);
					String[] clusterWords = split[2].split(",\\s*");
					removeTagAndIndex(clusterWords);
					StringBuilder scores = new StringBuilder();
					double topSimpleScore = 0;
					String topSimpleScoreDomain = "";
					double topCosineScore = 0;
					String topCosineScoreDomain = "";

					for (Entry<String, Map<String, Double>> domain : index.entrySet()) {
						String domainName = domain.getKey();
						double simpleScore = scoreSimple(domain.getValue(), clusterWords);
						if (simpleScore > topSimpleScore) {
							topSimpleScore = simpleScore;
							topSimpleScoreDomain = domainName;
						}
						double cosineScore = scoreCosine(domain.getValue(), clusterWords);
						if (cosineScore > topCosineScore) {
							topCosineScore = cosineScore;
							topCosineScoreDomain = domainName;
						}
						scores.append(domainName).append("|").append(df.format(simpleScore)).append("|")
								.append(df.format(cosineScore)).append(", ");
					}
					String topDomains = topSimpleScoreDomain + "|" + df.format(topSimpleScore) + ", "
							+ topCosineScoreDomain + "|" + df.format(topCosineScore);
					String outLine = clusterIndex + "\t" + topDomains + "\t" + scores.toString() + "\t"
							+ clusterWords.length + "\t" + split[2];
					synchronized (out) {
						out.write(outLine + "\n");
					}
				} catch (Exception e) {
					LOG.error("Error", e);
				} finally {
					latch.countDown();
				}
			});
			// keep the stream open until all parallel work is done
			latch.await();
		} catch (Exception e) {
			LOG.error("Error", e);
		}

	}

	private static List<String> readLines(File clusters) throws IOException {
		List<String> lines = Lists.newArrayList();
		try (BufferedReader in = Utils.openReader(clusters, InputMode.GZ)) {
			String line = null;
			while ((line = in.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	private static void removeTagAndIndex(String[] clusterWords) {
		for (int i = 0; i < clusterWords.length; i++) {
			String word = clusterWords[i];
			int firstHash = word.indexOf("#");
			if (firstHash != -1) {
				clusterWords[i] = word.substring(0, firstHash);
			}
		}
	}

	/*
	 * Simple score: (sum of all domain-weights of matching senses)/(size of
	 * cluster)
	 */
	private static double scoreSimple(Map<String, Double> index, String[] clusterWords) {
		int hits = 0;
		double score = 0;
		for (String word : clusterWords) {
			Double indexWeight = index.get(word.toLowerCase());
			if (indexWeight != null) {
				score += indexWeight;
				hits++;
			}
		}
		if (hits == 0) {
			return 0;
		} else {
			return score / clusterWords.length;
		}
	}

	/*
	 * Cosine distance, uses weights of the index and weights of 1 for the
	 * cluster words
	 */
	private static double scoreCosine(Map<String, Double> index, String[] clusterWords) {
		double clusterSize = clusterWords.length;
		double indexSize = 0;
		for (Double weight : index.values()) {
			indexSize += Math.abs(weight);
		}
		double score = 0;
		for (String word : clusterWords) {
			Double indexWeight = index.get(word.toLowerCase());
			if (indexWeight != null) {
				score += Math.abs(indexWeight);
			}
		}
		double result = score / (clusterSize * indexSize);
		return result;
	}

	private static Options createOptions() {
		Options options = new Options();
		Option clusters = Option.builder(OPTION_CLUSTERS_FILE).argName("clusters").desc("Clusters of senses").required()
				.hasArg().build();
		options.addOption(clusters);
		Option senses = Option.builder(OPTION_SENSES_FILE).argName("senses")
				.desc("Senses from babelnet crawlers (.csv, one per line). Three tab separated columns expected: sense, weight, domain")
				.required().hasArg().build();
		options.addOption(senses);
		Option output = Option.builder(OPTION_OUTPUT_FILE).argName("out")
				.desc("the clusters, ordered by their weight to the domain, capped at zero").required().hasArg()
				.build();
		options.addOption(output);
		return options;
	}
}
