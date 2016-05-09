package de.tudarmstadt.lt.structuredtopics.convert;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Sets;

import de.tudarmstadt.lt.structuredtopics.Utils;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser;
import de.tudarmstadt.lt.structuredtopics.ddts.SenseCluster;

public class DdtFilter {

	public static final String WORD_REGEX = ".*[a-zA-Z]+.*";

	public static void main(String[] args) {
		File ddt = new File(args[0]);
		File out = new File(args[1]);
		Set<String> posTags = new HashSet<>();
		if (args.length == 3) {
			posTags = Sets.newHashSet();
			posTags.add(args[2]);
		}
		Parser parser = new Parser();
		try {
			List<SenseCluster> clusters = parser.parseDDT(ddt);
			if (!posTags.isEmpty()) {
				System.out.println("Senses: " + clusters.size());
				System.out.println("filtering pos tags " + StringUtils.join(posTags));
				Utils.filterClustersByPosTag(clusters, posTags);
			}
			System.out.println("Senses: " + clusters.size());
			System.out.println("filtering by regex");
			Utils.filterClustersByRegEx(clusters, WORD_REGEX);
			System.out.println("Senses: " + clusters.size());
			System.out.println("writing");
			Utils.writeClustersToFile(clusters, out);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("done");
	}
}
