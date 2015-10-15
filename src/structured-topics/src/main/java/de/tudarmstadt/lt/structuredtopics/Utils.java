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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.common.collect.Sets;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;

public class Utils {
	private static final Set<String> POS_TAG_WHITELIST = Sets.newHashSet("NN", "NP", "JJ");

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
		Set<String> keysToRemove = Sets.newHashSetWithExpectedSize(clusters.size());
		for (Entry<String, Map<Integer, List<Feature>>> entry : clusters.entrySet()) {
			String senseWord = entry.getKey();
			boolean keep = false;
			for (String posTag : POS_TAG_WHITELIST) {
				if (senseWord.endsWith(posTag)) {
					keep = true;
					break;
				}
			}
			if (keep) {
				// filter senses
				Set<Integer> sensesToRemove = Sets.newHashSet();
				Map<Integer, List<Feature>> senses = entry.getValue();
				for (Entry<Integer, List<Feature>> senseEntry : senses.entrySet()) {
					// filter sense words
					List<Feature> features = senseEntry.getValue();
					for (int i = features.size() - 1; i >= 0; i--) {
						Feature feature = features.get(i);
						boolean keepFeature = false;
						for (String posTag : POS_TAG_WHITELIST) {
							if (feature.getWord().endsWith(posTag)) {
								keepFeature = true;
								break;
							}
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
					keep = false;
				}
			} else {
				// sense will be removed
				keysToRemove.add(senseWord);
			}
		}
		for (String s : keysToRemove) {
			clusters.remove(s);
		}
	}

}
