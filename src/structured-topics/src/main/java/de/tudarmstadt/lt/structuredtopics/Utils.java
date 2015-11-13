package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

import com.google.common.collect.Sets;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;

public class Utils {

	private static interface Filter {
		boolean filter(String word);
	}

	private static class PosTagFilter implements Filter {
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

	private static class RegexFilter implements Filter {

		private Matcher matcher;

		public RegexFilter(String regex) {
			this.matcher = Pattern.compile(regex).matcher("");
		}

		@Override
		public boolean filter(String word) {
			matcher.reset(word);
			boolean filter = !matcher.matches();
			if (filter) {
				System.err.println(word);
			}
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

	public static BufferedWriter openWriter(File output) throws IOException {
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
		filterClusters(clusters, new PosTagFilter());
	}

	public static void filterClustersByRegEx(Map<String, Map<Integer, List<Feature>>> clusters, String regex) {
		filterClusters(clusters, new RegexFilter(regex));
	}

	private static void filterClusters(Map<String, Map<Integer, List<Feature>>> clusters, Filter filter) {
		Set<String> keysToRemove = Sets.newHashSetWithExpectedSize(clusters.size());
		for (Entry<String, Map<Integer, List<Feature>>> entry : clusters.entrySet()) {
			String senseWord = entry.getKey();
			boolean keepSenseWord = false;
			if (!filter.filter(senseWord)) {
				keepSenseWord = true;
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
						boolean keepFeature = false;
						if (!filter.filter(feature.getWord())) {
							keepFeature = true;
							break;
						}
						if (!keepFeature) {
							features.remove(i);
						}
					}
					// if no words left -> remove sense
					if (features.isEmpty()) {
						sensesToRemove.add(senseEntry.getKey());
					}
				}
				for (Integer i : sensesToRemove) {
					senses.remove(i);
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
	}

	public static void writeClustersToFile(Map<String, Map<Integer, List<Feature>>> clusters, File out)
			throws IOException {
		try (BufferedWriter writer = openWriter(out)) {
			for (Entry<String, Map<Integer, List<Feature>>> senseClusters : clusters.entrySet()) {
				String senseWord = senseClusters.getKey();
				for (Entry<Integer, List<Feature>> senseCluster : senseClusters.getValue().entrySet()) {
					Integer senseId = senseCluster.getKey();
					writer.write(senseWord);
					writer.write("\t");
					writer.write(senseId.toString());
					writer.write("\t");
					for (Feature f : senseCluster.getValue()) {
						writer.write(f.getWord() + ":" + f.getWeight());
						writer.write(", ");
					}
					writer.write("\n");
				}
			}
		}
	}

}
