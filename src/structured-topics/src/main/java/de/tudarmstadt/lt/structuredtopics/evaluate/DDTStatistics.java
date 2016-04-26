package de.tudarmstadt.lt.structuredtopics.evaluate;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

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

import com.google.common.collect.Sets;

import de.tudarmstadt.lt.structuredtopics.ddts.ClusterWord;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser.DDTIterator;
import de.tudarmstadt.lt.structuredtopics.ddts.Sense;
import de.tudarmstadt.lt.structuredtopics.ddts.SenseCluster;

/**
 * Prints different statistics for a dtt to the log.
 *
 */
public class DDTStatistics {

	private static final Logger LOG = LoggerFactory.getLogger(DDTStatistics.class);

	private static final String OPTION_DDT = "in";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File ddt = new File(cl.getOptionValue(OPTION_DDT));
			if (!ddt.exists()) {
				LOG.error("File not found: {}", ddt.getAbsolutePath());
				return;
			}
			StringBuilder b = new StringBuilder();
			printStatistics(ddt, b);
			LOG.info("Statistics for {}:\n{}", ddt.getAbsolutePath(), b.toString());
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

	public static DDTStats collectStats(File ddt) {
		int totalSenses = 0;
		int totalClusterWords = 0;
		Set<String> uniqueSenseWords = Sets.newHashSetWithExpectedSize(1000 * 1000);
		Set<String> uniqueClusterWords = Sets.newHashSetWithExpectedSize(1000 * 1000);
		try (DDTIterator it = new Parser().iterateDDT(ddt)) {
			while (it.hasNext()) {
				SenseCluster cluster = it.next();
				if (cluster == null) {
					continue;
				}
				Sense sense = cluster.getSense();
				uniqueSenseWords.add(sense.getFullWord());
				totalSenses++;
				totalClusterWords += cluster.getClusterWords().size();
				for (ClusterWord clusterWord : cluster.getClusterWords()) {
					uniqueClusterWords.add(clusterWord.getFullWord());
				}
			}
		} catch (IOException e) {
			LOG.error("Error", e);
		}
		DDTStats stats = new DDTStats();
		stats.totalSenses = totalSenses;
		stats.totalClusterWords = totalClusterWords;
		stats.uniqueClusterWords = uniqueClusterWords.size();
		stats.uniqueSenseWords = uniqueSenseWords.size();
		stats.averageClusterSize = (double) totalClusterWords / totalSenses;
		return stats;
	}

	private static void printStatistics(File ddt, StringBuilder b) {
		DDTStats stats = collectStats(ddt);
		b.append("Total senses: " + stats.totalSenses + "\n");
		b.append("Unique sense words: " + stats.uniqueSenseWords + "\n");
		b.append("Total cluster words: " + stats.totalClusterWords + "\n");
		b.append("Unique cluster words: " + stats.uniqueClusterWords + "\n");
		b.append("Average cluster size: " + stats.averageClusterSize);
	}

	private static Options createOptions() {
		Options options = new Options();
		Option ddt = Option.builder(OPTION_DDT).argName("ddt file").desc("The input ddt").hasArg().required().build();
		options.addOption(ddt);
		return options;
	}

	public static class DDTStats {
		public int totalSenses;
		public int uniqueSenseWords;
		public int totalClusterWords;
		public int uniqueClusterWords;
		public double averageClusterSize;

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
