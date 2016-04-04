package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;

public class Utils {

	private static final Logger LOG = LoggerFactory.getLogger(Utils.class);

	private static interface Filter {
		boolean filter(String word);
	}

	protected static class PosTagFilter implements Filter {
		private static final Set<String> POS_TAG_WHITELIST = Sets.newHashSet("NN", "NP", "JJ");

		@Override
		public boolean filter(String word) {
			for (String tag : POS_TAG_WHITELIST) {
				if (word.endsWith(tag)) {
					return false;
				}
			}
			return true;
		}
	}

	protected static class RegexFilter implements Filter {

		private Matcher matcher;

		public RegexFilter(String regex) {
			this.matcher = Pattern.compile(regex).matcher("");
		}

		@Override
		public boolean filter(String word) {
			int indexOfFirstHash = word.indexOf("#");
			if (indexOfFirstHash != -1) {
				String withoutPosTag = word.substring(0, indexOfFirstHash);
				matcher.reset(withoutPosTag);
			} else {
				matcher.reset(word);
			}
			boolean filter = !matcher.matches();
			return filter;
		}

	}

	public static BufferedReader openReader(File input, InputMode mode) throws IOException {
		InputStream in = new FileInputStream(input);
		if (mode == InputMode.GZ) {
			in = new GZIPInputStream(in);
		}
		// TODO if required, encoding should be passed here
		Reader reader = new InputStreamReader(in);
		return new BufferedReader(reader);
	}

	public static BufferedWriter openGzipWriter(File output) throws IOException {
		OutputStream out = new FileOutputStream(output);
		out = new GZIPOutputStream(out);
		// TODO if required, encoding should be passed here
		Writer writer = new OutputStreamWriter(out);
		return new BufferedWriter(writer);
	}

	public static int countSenses(Map<String, Map<Integer, List<Feature>>> clusters) {
		int count = 0;
		for (Map<Integer, List<Feature>> v : clusters.values()) {
			count += v.size();
		}
		return count;
	}

	public static void filterClustersByPosTag(Map<String, Map<Integer, List<Feature>>> clusters) {
		LOG.info("Filtering by POS-Tag");
		filterClusters(clusters, new PosTagFilter());
	}

	public static void filterClustersByRegEx(Map<String, Map<Integer, List<Feature>>> clusters, String regex) {
		LOG.info("Filtering by regex {}", regex);
		filterClusters(clusters, new RegexFilter(regex));
	}

	private static void filterClusters(Map<String, Map<Integer, List<Feature>>> clusters, Filter filter) {
		Set<String> keysToRemove = Sets.newHashSetWithExpectedSize(clusters.size());
		int removedFeatures = 0;
		int removedSenses = 0;
		for (Entry<String, Map<Integer, List<Feature>>> entry : clusters.entrySet()) {
			String senseWord = entry.getKey();
			boolean keepSenseWord = false;
			try {
				if (!filter.filter(senseWord)) {
					keepSenseWord = true;
				}
			} catch (Exception e) {
				LOG.error("Filter {} threw an exeption while filtering word {}. Word will be removed",
						filter.getClass(), senseWord, e);
			}
			if (keepSenseWord) {
				// filter senses
				Set<Integer> sensesToRemove = Sets.newHashSet();
				Map<Integer, List<Feature>> senses = entry.getValue();
				for (Entry<Integer, List<Feature>> senseEntry : senses.entrySet()) {
					// filter sense words
					List<Feature> features = senseEntry.getValue();
					for (int i = features.size() - 1; i >= 0; i--) {
						Feature feature = features.get(i);
						if (filter.filter(feature.getWord())) {
							features.remove(i);
							removedFeatures++;
						}
					}
					// if no words left -> remove sense
					if (features.isEmpty()) {
						sensesToRemove.add(senseEntry.getKey());
					}
				}
				for (Integer i : sensesToRemove) {
					senses.remove(i);
					removedSenses++;
				}
				// if all senses are empty, remove entire sense
				if (senses.isEmpty()) {
					keysToRemove.add(senseWord);
				}
			} else {
				keysToRemove.add(senseWord);
			}
		}
		for (String s : keysToRemove) {
			clusters.remove(s);
		}
		LOG.info("Filtered {} features and {} entire senses", removedFeatures, removedSenses);
	}

	public static void writeClustersToFile(Map<String, Map<Integer, List<Feature>>> clusters, File out)
			throws IOException {
		try (BufferedWriter writer = openGzipWriter(out)) {
			for (Entry<String, Map<Integer, List<Feature>>> senseClusters : clusters.entrySet()) {
				String senseWord = senseClusters.getKey();
				for (Entry<Integer, List<Feature>> senseCluster : senseClusters.getValue().entrySet()) {
					Integer senseId = senseCluster.getKey();
					writer.write(senseWord);
					writer.write("\t");
					writer.write(senseId.toString());
					writer.write("\t");
					for (Feature f : senseCluster.getValue()) {
						if (f.getSenseId() != null) {
							writer.write(f.getWord() + "#" + f.getSenseId() + ":" + f.getWeight());
						} else {
							writer.write(f.getWord());
						}
						writer.write(", ");
					}
					writer.write("\n");
				}
			}
		}
	}

	public static Set<String> loadUniqueLines(File file) {
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

}
