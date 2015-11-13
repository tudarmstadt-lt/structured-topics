package de.tudarmstadt.lt.structuredtopics.convert;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import de.tudarmstadt.lt.structuredtopics.Feature;
import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class DdtFilter {
	public static void main(String[] args) {
		File in = new File(args[0]);
		File out = new File(args[1]);
		Parser parser = new Parser();
		Map<String, Map<Integer, List<Feature>>> clusters = parser.readClusters(in, InputMode.GZ);
		System.out.println("Senses: " + Utils.countSenses(clusters));
		System.out.println("filtering pos tags");
		Utils.filterClustersByPosTag(clusters);
		System.out.println("Senses: " + Utils.countSenses(clusters));
		System.out.println("filtering by regex");
		Utils.filterClustersByRegEx(clusters, ".*[a-zA-Z]+.*");
		System.out.println("Senses: " + Utils.countSenses(clusters));
		System.out.println("writing");
		try {
			Utils.writeClustersToFile(clusters, out);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("done");
	}
}
