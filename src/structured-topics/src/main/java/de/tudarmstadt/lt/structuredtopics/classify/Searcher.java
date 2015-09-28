package de.tudarmstadt.lt.structuredtopics.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Searcher {

	public static void main(String[] args) {
		Analyzer analyzer = new StandardAnalyzer();
		try {

			File clustersFile = new File(args[1]);
			Map<Integer, List<String>> clusters = Maps.newHashMap();
			System.out.println("Loading clusters from " + clustersFile.getAbsolutePath() + " for lookup");
			try (BufferedReader in = new BufferedReader(new FileReader(clustersFile))) {
				String line = null;
				while ((line = in.readLine()) != null) {
					String[] split = line.split("\t");
					Integer clusterId = Integer.valueOf(split[0]);
					String[] words = split[1].split(", ");
					ArrayList<String> wordsList = Lists.newArrayList(words);
					clusters.put(clusterId, wordsList.subList(0, Math.min(10, wordsList.size())));
				}
			}

			String querystr = args[2];
			Query q = new QueryParser("word", analyzer).parse(querystr);
			System.out.println("Searching for query " + q.toString());
			Directory index = FSDirectory.open(new File(args[0]).toPath());

			int hitsPerPage = 10;
			IndexReader reader = DirectoryReader.open(index);
			IndexSearcher searcher = new IndexSearcher(reader);
			TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage);
			searcher.search(q, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
			System.out.println("Found " + hits.length + " hits.");
			for (int i = 0; i < hits.length; ++i) {
				int docId = hits[i].doc;
				float score = hits[i].score;
				Document d = searcher.doc(docId);
				String clusterId = d.get("cluster_id");
				System.out.println("[" + score + "] cluster-id: " + clusterId);
				System.out.println("\t words: " + words(clusters, clusterId));
				System.out.println();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static String words(Map<Integer, List<String>> clusters, String clusterId) {
		StringBuilder b = new StringBuilder();
		List<String> clusterWords = clusters.get(Integer.valueOf(clusterId));
		if (clusterWords == null) {
			return "<no words found>";
		}
		for (String word : clusterWords) {
			b.append(word).append(", ");
		}
		return b.toString();
	}
}
