package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import de.tudarmstadt.lt.structuredtopics.Utils;
import de.tudarmstadt.lt.structuredtopics.evaluate.DDTStatistics.DDTStats;

public class Experiment2ResultAggregator {

	private static final String OPTION_RESULT_DIR = "resultDir";
	private static final String OPTION_RESULT_FILE = "out";

	private static final Logger LOG = LoggerFactory.getLogger(Experiment2ResultAggregator.class);

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File resultDir = new File(cl.getOptionValue(OPTION_RESULT_DIR));
			File out = new File(cl.getOptionValue(OPTION_RESULT_FILE));
			aggregateResults(resultDir, out);
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

	private static void aggregateResults(File resultDir, File resultFile) {
		DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		df.setMaximumFractionDigits(340);
		Map<String, Map<Boolean, DDTStats>> ddtStats = ddtStats(new File(resultDir, "1_ddts"));
		Map<String, Map<Boolean, Map<String, Integer>>> simStats = similaritiesStats(
				new File(resultDir, "2_similarities"));
		Map<String, Map<Boolean, Map<String, Map<String, Integer>>>> clusterStats = clusterStats(
				new File(resultDir, "3_clusters"));
		Map<String, Map<Boolean, Map<String, Map<String, MappingStats>>>> mappingsStats = mappingsStats(
				new File(resultDir, "4_mappings"));
		LOG.info("Writing results to {}", resultFile.getAbsolutePath());
		try (BufferedWriter out = Utils.openWriter(resultFile)) {
			out.write(
					"ddtName,filtered,totalSenses,uniqueSenseWords,totalClusterWords,uniqueClusterWords,averageClusterSize,similarityMetric,numberOfEdges,cwOption,numberOfClusters,maxOverlap,avgOverlap,totalOverlap,maxCosineScore,totalCosineScore\n");
			for (Entry<String, Map<Boolean, Map<String, Map<String, MappingStats>>>> e : mappingsStats.entrySet()) {
				String ddtName = e.getKey();
				for (Entry<Boolean, Map<String, Map<String, MappingStats>>> e2 : e.getValue().entrySet()) {
					boolean filtered = e2.getKey();
					for (Entry<String, Map<String, MappingStats>> e3 : e2.getValue().entrySet()) {
						String similarityMetric = e3.getKey();
						for (Entry<String, MappingStats> e4 : e3.getValue().entrySet()) {
							String cwOption = e4.getKey();
							try {
								MappingStats stats = e4.getValue();
								DDTStats ddtStat = ddtStats.get(ddtName).get(filtered);
								Integer numberOfEdges = simStats.get(ddtName).get(filtered).get(similarityMetric);
								Integer numberOfClusters = clusterStats.get(ddtName).get(filtered).get(similarityMetric)
										.get(cwOption);
								String line = ddtName + "," + filtered + "," + ddtStat.totalSenses + ","
										+ ddtStat.uniqueSenseWords + "," + ddtStat.totalClusterWords + ","
										+ ddtStat.uniqueClusterWords + "," + ddtStat.averageClusterSize + ","
										+ similarityMetric + "," + numberOfEdges + "," + cwOption + ","
										+ numberOfClusters + "," + df.format(stats.maxOverlap) + ","
										+ df.format(stats.totalOverlap / numberOfClusters) + ","
										+ df.format(stats.totalOverlap) + "," + df.format(stats.maxCosineScore) + ","
										+ df.format(stats.totalCosineScore) + "\n";
								out.write(line);
							} catch (Exception ex) {
								LOG.error(
										"Error while writing data for ddt {}, filtered {}, similarity {}, cwOption {}",
										ddtName, filtered, similarityMetric, cwOption, e);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			LOG.error("Error while writing results to {}", resultFile.getAbsolutePath(), e);
		}
	}

	private static Map<String, Map<Boolean, DDTStats>> ddtStats(File ddtDir) {
		// ddt - filtered - similarityMetric - lineCount
		Map<String, Map<Boolean, DDTStats>> ddtStats = Maps.newHashMap();
		File[] ddts = ddtDir.listFiles();
		Arrays.stream(ddts).parallel().forEach(ddt -> {
			LOG.debug("Collecting stats for ddt {}", ddt.getAbsolutePath());
			boolean filtered = ddt.getName().contains("filtered");
			String ddtName = ddtNameFrom(ddt.getName());
			DDTStats stats = DDTStatistics.collectStats(ddt);
			Map<Boolean, DDTStats> filteredStats = ddtStats.get(ddtName);
			if (filteredStats == null) {
				filteredStats = Maps.newHashMap();
				ddtStats.put(ddtName, filteredStats);
			}
			filteredStats.put(filtered, stats);
		});
		LOG.debug("All Stats collected");
		return ddtStats;
	}

	private static Map<String, Map<Boolean, Map<String, Integer>>> similaritiesStats(File simDir) {
		// ddt - filtered - similarityMetric - lineCount
		Map<String, Map<Boolean, Map<String, Integer>>> simStats = Maps.newHashMap();
		File[] similarities = simDir.listFiles();
		Arrays.stream(similarities).parallel().forEach(similaritiy -> {
			LOG.debug("Collecting stats for similarities {}", similaritiy.getAbsolutePath());
			String simName = similaritiy.getName();
			boolean filtered = simName.contains("filtered");
			String metric = simName.split("[-]")[0];
			String ddtName = ddtNameFrom(simName);
			int lines = Utils.countLines(similaritiy);
			Map<Boolean, Map<String, Integer>> filteredStats = simStats.get(ddtName);
			if (filteredStats == null) {
				filteredStats = Maps.newHashMap();
				simStats.put(ddtName, filteredStats);
			}
			Map<String, Integer> metricStats = filteredStats.get(filtered);
			if (metricStats == null) {
				metricStats = Maps.newHashMap();
				filteredStats.put(filtered, metricStats);
			}
			metricStats.put(metric, lines);
		});
		return simStats;
	}

	private static Map<String, Map<Boolean, Map<String, Map<String, Integer>>>> clusterStats(File clusterDir) {
		// ddt - filtered - similarityMetric - cwOption - lineCount
		Map<String, Map<Boolean, Map<String, Map<String, Integer>>>> clusterStats = Maps.newHashMap();
		File[] clusters = clusterDir.listFiles();
		Arrays.stream(clusters).parallel().forEach(cluster -> {
			LOG.debug("Collecting stats for clusters {}", cluster.getAbsolutePath());
			String clusterName = cluster.getName();
			boolean filtered = clusterName.contains("filtered");
			String cwOption = clusterName.replace("clusters-", "").split("[-]")[0];
			String metric = clusterName.replace("clusters-", "").split("[-]")[1];
			String ddtName = ddtNameFrom(clusterName);
			int lines = Utils.countLines(cluster);
			Map<Boolean, Map<String, Map<String, Integer>>> filteredStats = clusterStats.get(ddtName);
			if (filteredStats == null) {
				filteredStats = Maps.newHashMap();
				clusterStats.put(ddtName, filteredStats);
			}
			Map<String, Map<String, Integer>> metricStats = filteredStats.get(filtered);
			if (metricStats == null) {
				metricStats = Maps.newHashMap();
				filteredStats.put(filtered, metricStats);
			}
			Map<String, Integer> cwOptionStats = metricStats.get(metric);
			if (cwOptionStats == null) {
				cwOptionStats = Maps.newHashMap();
				metricStats.put(metric, cwOptionStats);
			}
			cwOptionStats.put(cwOption, lines);
		});
		return clusterStats;
	}

	private static Map<String, Map<Boolean, Map<String, Map<String, MappingStats>>>> mappingsStats(File clusterDir) {
		// ddt - filtered - similarityMetric - cwOption - lineCount
		Map<String, Map<Boolean, Map<String, Map<String, MappingStats>>>> mappingsStats = Maps.newHashMap();
		File[] clusters = clusterDir.listFiles();
		Arrays.stream(clusters).parallel().forEach(mappings -> {
			LOG.debug("Collecting stats for mappings {}", mappings.getAbsolutePath());
			String mappingName = mappings.getName();
			boolean filtered = mappingName.contains("filtered");
			String cwOption = mappingName.replace("domains-clusters-", "").split("[-]")[0];
			String metric = mappingName.replace("domains-clusters-", "").split("[-]")[1];
			String ddtName = ddtNameFrom(mappingName);
			MappingStats stats = calculateStatsForMapping(mappings);
			Map<Boolean, Map<String, Map<String, MappingStats>>> filteredStats = mappingsStats.get(ddtName);
			if (filteredStats == null) {
				filteredStats = Maps.newHashMap();
				mappingsStats.put(ddtName, filteredStats);
			}
			Map<String, Map<String, MappingStats>> metricStats = filteredStats.get(filtered);
			if (metricStats == null) {
				metricStats = Maps.newHashMap();
				filteredStats.put(filtered, metricStats);
			}
			Map<String, MappingStats> cwOptionStats = metricStats.get(metric);
			if (cwOptionStats == null) {
				cwOptionStats = Maps.newHashMap();
				metricStats.put(metric, cwOptionStats);
			}
			cwOptionStats.put(cwOption, stats);
		});
		return mappingsStats;
	}

	private static MappingStats calculateStatsForMapping(File mappings) {
		double totalOverlap = 0;
		double maxOverlap = 0;
		double totalCosineScore = 0;
		double maxCosineScore = 0;
		try (BufferedReader in = Utils.openReader(mappings)) {
			String line = null;
			while ((line = in.readLine()) != null) {
				String[] columns = line.split("\\t");
				double overlap = Double.parseDouble(columns[2]);
				maxOverlap = Math.max(maxOverlap, overlap);
				totalOverlap += overlap;
				double cosineScore = Double.parseDouble(columns[8]);
				maxCosineScore = Math.max(maxCosineScore, cosineScore);
				totalCosineScore += cosineScore;
			}
		} catch (IOException e) {
			LOG.error("Error while reading mapping file {}", mappings.getAbsolutePath(), e);
		}
		MappingStats stats = new MappingStats();
		stats.totalOverlap = totalOverlap;
		stats.maxOverlap = maxOverlap;
		stats.totalCosineScore = totalCosineScore;
		stats.maxCosineScore = maxCosineScore;
		return stats;
	}

	private static String ddtNameFrom(String resultFileName) {
		return resultFileName.replace(".csv", "").replace(".gz", "").replace("filtered-", "").replace("all-", "")
				.replace("lucene-", "").replace("clusters-", "").replace("TOP-", "").replace("DIST_LOG-", "")
				.replace("DIST_NOLOG-", "").replace("similarities-", "").replace("domains-", "");
	}

	private static Options createOptions() {
		Options options = new Options();
		Option resultDir = Option.builder(OPTION_RESULT_DIR).argName("resultDir").desc("Directory with result folders")
				.required().hasArg().build();
		options.addOption(resultDir);
		Option out = Option.builder(OPTION_RESULT_FILE).argName("out")
				.desc("File where the results will be aggregated in csv format").required().hasArg().build();
		options.addOption(out);
		return options;
	}

	public static class MappingStats {

		public double maxCosineScore;
		public double totalCosineScore;
		public double totalOverlap;
		public double maxOverlap;

		@Override
		public String toString() {
			return ReflectionToStringBuilder.toString(this);
		}

		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}
	}
}
