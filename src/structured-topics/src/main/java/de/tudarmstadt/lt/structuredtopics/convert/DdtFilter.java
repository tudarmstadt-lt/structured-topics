package de.tudarmstadt.lt.structuredtopics.convert;

import java.io.File;
import java.io.IOException;
import java.util.List;

import de.tudarmstadt.lt.structuredtopics.Utils;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser;
import de.tudarmstadt.lt.structuredtopics.ddts.SenseCluster;

public class DdtFilter {

	public static final String WORD_REGEX = ".*[a-zA-Z]+.*";

	public static void main(String[] args) {
		File ddt = new File(args[0]);
		File out = new File(args[1]);
		Parser parser = new Parser();
		try {
			List<SenseCluster> clusters = parser.parseDDT(ddt);
			// System.out.println("Senses: " + Utils.countSenses(clusters));
			// System.out.println("filtering pos tags");
			// Utils.filterClustersByPosTag(clusters);
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
