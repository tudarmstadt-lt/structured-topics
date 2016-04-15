package de.tudarmstadt.lt.structuredtopics.ddts;

import static org.apache.commons.lang3.StringUtils.lastIndexOf;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.LineIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import de.tudarmstadt.lt.structuredtopics.Utils;

public class Parser {
	private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

	public List<SenseCluster> parseDDT(File ddt) throws IOException {
		List<SenseCluster> senseClusters = new ArrayList<>(100000);
		try (BufferedReader in = Utils.openReader(ddt)) {
			String line = null;
			int count = 0;
			while ((line = in.readLine()) != null) {
				count++;
				if (count % 1000 == 0) {
					LOG.info("Parsing cluster {}", count);
				}
				SenseCluster cluster = null;
				try {
					cluster = parseSenseClusterFromLine(line);
				} catch (Exception e) {
					LOG.error("Unexpected error while parsing line {} : {}", count, line, e);
				}
				if (cluster != null) {
					senseClusters.add(cluster);
				} else {
					LOG.warn("Unable to parse line {}: {}", count, line);
				}
			}
		}
		return senseClusters;
	}

	public DDTIterator iterateDDT(File ddt) throws IOException {
		return new DDTIterator(Utils.openReader(ddt));
	}

	/**
	 * Iterates over the senses of an DDT without keeping the entire file in
	 * memory. The Iterator may return null for some clusters if a line can not
	 * be parsed from the ddt.
	 *
	 */
	public class DDTIterator implements Iterator<SenseCluster>, Closeable {

		private org.apache.commons.io.LineIterator it;

		private BufferedReader in;
		private int count = 0;

		public DDTIterator(BufferedReader in) {
			this.in = in;
			it = new LineIterator(in);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public SenseCluster next() {
			String line = it.next();
			count++;
			try {
				return parseSenseClusterFromLine(line);
			} catch (Exception e) {
				LOG.error("Unexpected error while parsing line {} : {}", count, line, e);
				return null;
			}
		}

		@Override
		public void close() throws IOException {
			in.close();
		}

	}

	@VisibleForTesting
	protected SenseCluster parseSenseClusterFromLine(String line) {
		String[] columns = line.split("\t");
		if (columns.length < 3) {
			return null;
		}
		List<SingleWord> senseWords = parseSenseWords(columns[0]);
		Integer senseId;
		try {
			senseId = Integer.valueOf(columns[1]);
		} catch (NumberFormatException e) {
			// some ids were saved in double format. However, this should be the
			// exception not the default as the conversion from string to double
			// to integer is more expensive
			senseId = Double.valueOf(columns[1]).intValue();
		}
		Sense sense = new Sense(senseWords, senseId);
		List<ClusterWord> cluster = parseClusterWords(columns[2]);
		return new SenseCluster(sense, cluster);
	}

	private List<SingleWord> parseSenseWords(String senseColumn) {
		List<SingleWord> senseWords = Lists.newArrayListWithCapacity(1);
		String[] words = senseColumn.split("\\s+");
		for (String wordRaw : words) {
			int lastHash = lastIndexOf(wordRaw, "#");
			if (lastHash != -1) {
				String text = wordRaw.substring(0, lastHash);
				String pos = wordRaw.substring(lastHash + 1);
				SingleWord word = new SingleWord(text, pos);
				senseWords.add(word);
			} else {
				SingleWord word = new SingleWord(wordRaw, null);
				senseWords.add(word);
			}
		}
		return senseWords;
	}

	private static final Pattern SENSEID_WEIGHT_PATTERN = Pattern.compile("\\d+[:]\\d+[.]\\d+");

	private List<ClusterWord> parseClusterWords(String clusterColumn) {
		List<ClusterWord> clusterWords = new ArrayList<>(100);
		// words are a csv list
		String[] clusterWordsRaw = clusterColumn.split("[,]\\s*");
		for (String wordsRaw : clusterWordsRaw) {
			List<SingleWord> words = new ArrayList<>(1);
			Integer relatedSenseId = null;
			Double weight = null;
			// multi-words are separated by whitespace
			for (String wordRaw : wordsRaw.split("\\s+")) {
				String[] sections = wordRaw.split("[#]");
				String text = sections[0];
				String pos = null;
				for (int i = 1; i < sections.length; i++) {
					String section = sections[i];
					if (SENSEID_WEIGHT_PATTERN.matcher(section).matches()) {
						String[] senseIdWeightRaw = section.split("[:]");
						relatedSenseId = Integer.valueOf(senseIdWeightRaw[0]);
						weight = Double.valueOf(senseIdWeightRaw[1]);
					} else {
						// in case of multiple sections, the last one is the
						// pos-tag. The previous one will be overridden.
						pos = section;
					}
				}
				SingleWord word = new SingleWord(text, pos);
				words.add(word);
			}
			ClusterWord clusterWord = new ClusterWord(words, relatedSenseId, weight);
			clusterWords.add(clusterWord);
		}
		return clusterWords;
	}
}
