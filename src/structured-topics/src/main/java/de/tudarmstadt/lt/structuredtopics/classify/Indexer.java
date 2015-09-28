package de.tudarmstadt.lt.structuredtopics.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class Indexer {
	/**
	 * Arg1: senseClusters Arg2: Index directory
	 * 
	 */
	public static void main(String[] args) {
		File senseClusters = new File(args[0]);
		File indexDir = new File(args[1]);

		Analyzer analyzer = new StandardAnalyzer();
		try {
			Directory index = FSDirectory.open(indexDir.toPath());
			IndexWriterConfig config = new IndexWriterConfig(analyzer);

			try (BufferedReader in = new BufferedReader(new FileReader(senseClusters))) {
				try (IndexWriter w = new IndexWriter(index, config)) {
					String line = null;
					int count = 0;
					while ((line = in.readLine()) != null) {
						String[] split = line.split("\t");
						if (split.length < 2) {
							continue;
						}
						if (count++ % 10 == 0) {
							System.out.println("Indexing cluster " + count);
						}
						Document doc = new Document();
						String clusterId = split[0];
						doc.add(new TextField("cluster_id", clusterId, Store.YES));
						String[] words = split[1].split(", ");
						for (int i = 0; i < words.length; i++) {
							String word = words[i];
							int firstHash = word.indexOf("#");
							if (firstHash != -1) {
								word = word.substring(0, firstHash);
							}
							doc.add(new StringField("word", word, Store.NO));
						}
						w.addDocument(doc);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
