package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedWriter;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.tudarmstadt.lt.structuredtopics.Feature;
import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class SenseSimilarityCalculator {

	private static final Logger LOG = LoggerFactory.getLogger(SenseSimilarityCalculator.class);

	/**
	 * Arg1: ddt-file (gz). Arg2: output-file. Arg3: collect n most similar
	 * senses per sense (must be an integer).
	 * 
	 */
	public static void main(String[] args) {
		File senseClusters = new File(args[0]);
		File output = new File(args[1]);
		int collectSimilarSensesPerSense = Integer.parseInt(args[2]);
		Analyzer analyzer = new KeywordAnalyzer();
		try {
			RAMDirectory index = new RAMDirectory();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			Parser parser = new Parser();
			Map<String, Map<Integer, List<Feature>>> clusters = parser.readClusters(senseClusters, InputMode.GZ);
			int total = Utils.countSenses(clusters);
			Stopwatch watch = Stopwatch.createStarted();
			LOG.info("Starting indexing");
			try (IndexWriter w = new IndexWriter(index, config)) {
				int count = 0;
				for (Entry<String, Map<Integer, List<Feature>>> cluster : clusters.entrySet()) {
					String senseWord = cluster.getKey();
					Document senseDocument = new Document();
					for (Entry<Integer, List<Feature>> sense : cluster.getValue().entrySet()) {
						if (count++ % 100 == 0) {
							LOG.info("indexing sense {}/{}", count, total);
							LOG.info("Index size: {}bytes", index.ramBytesUsed());
						}
						Integer senseId = sense.getKey();
						senseDocument.add(new StringField("sense_word_id", senseWord + "#" + senseId, Store.YES));
						for (Feature f : sense.getValue()) {
							String word = f.getWord();
							senseDocument.add(new StringField("sense_cluster_word", word, Store.NO));
						}
					}
					try {
						w.addDocument(senseDocument);
					} catch (Exception e) {
						LOG.warn("Error while adding document for cluster {} to index {}.", count, senseDocument, e);
					}
				}
				w.commit();
			}
			LOG.info("Creating index took {}ms", watch.elapsed(TimeUnit.MILLISECONDS));
			watch = Stopwatch.createStarted();
			IndexReader reader = DirectoryReader.open(index);
			IndexSearcher searcher = new IndexSearcher(reader);
			CountDownLatch latch = new CountDownLatch(clusters.entrySet().size());
			try (BufferedWriter out = Utils.openGzipWriter(output)) {
				AtomicInteger count = new AtomicInteger();
				clusters.entrySet().parallelStream().forEach(cluster -> {
					try {
						String senseWord = cluster.getKey();
						for (Entry<Integer, List<Feature>> sense : cluster.getValue().entrySet()) {
							if (count.incrementAndGet() % 100 == 0) {
								LOG.info("Searching similarities for sense {}/{}", count, total);
							}
							Integer senseId = sense.getKey();
							String senseWordId1 = senseWord + "#" + senseId;
							BooleanQuery.Builder builder = new BooleanQuery.Builder();
							BooleanQuery.setMaxClauseCount(1000000);
							for (Feature f : sense.getValue()) {
								String word = f.getWord();
								builder.add(new TermQuery(new Term("sense_cluster_word", word)), Occur.SHOULD);
							}
							BooleanQuery query = builder.build();
							TopDocs result = searcher.search(query, collectSimilarSensesPerSense);
							for (ScoreDoc s : result.scoreDocs) {
								String senseWordId2 = reader.document(s.doc).getField("sense_word_id").stringValue();
								if (senseWordId1.equals(senseWordId2)) {
									// ignore self-similarity
									continue;
								}
								float score = s.score;
								String similarity = senseWordId1 + "\t" + senseWordId2 + "\t" + score;
								synchronized (out) {
									out.write(similarity);
									out.write("\n");
								}
							}
						}
					} catch (Exception e) {
						LOG.error("Error", e);
					} finally {
						latch.countDown();
					}
				});

				// don't close the stream before all work is done
				latch.await();
				LOG.info("Searching similarities took {}ms", watch.elapsed(TimeUnit.MILLISECONDS));
				LOG.info("Done");
			}
		} catch (Exception e) {
			LOG.error("Error", e);
		}

	}

}
