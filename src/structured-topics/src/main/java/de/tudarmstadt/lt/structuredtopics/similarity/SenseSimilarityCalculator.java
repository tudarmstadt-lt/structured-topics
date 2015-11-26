package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import de.tudarmstadt.lt.structuredtopics.Feature;
import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Parser;
import de.tudarmstadt.lt.structuredtopics.Utils;

public class SenseSimilarityCalculator {

	private static final String OPTION_ALL_SIMILARITIES = "ALL";
	private static final String OPTION_SIMILAR_SENSES = "N";
	private static final String OPTION_OUT_FILE = "out";
	private static final String OPTION_IN_FILE = "in";
	private static final Logger LOG = LoggerFactory.getLogger(SenseSimilarityCalculator.class);

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine line = new DefaultParser().parse(options, args, true);
			File senseClusters = new File(line.getOptionValue(OPTION_IN_FILE));
			File output = new File(line.getOptionValue(OPTION_OUT_FILE));
			Parser parser = new Parser();
			Map<String, Map<Integer, List<Feature>>> clusters = parser.readClusters(senseClusters, InputMode.GZ);
			Analyzer analyzer = new KeywordAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			int total = Utils.countSenses(clusters);
			if (line.hasOption(OPTION_ALL_SIMILARITIES)) {
				LOG.info("Calculating all similarities");
				writeAllSimilarities(output, clusters, total);
			} else if (line.hasOption(OPTION_SIMILAR_SENSES)) {
				LOG.info("Calculating similarities using index");
				Stopwatch watch = Stopwatch.createStarted();
				LOG.info("Starting indexing");
				RAMDirectory index = new RAMDirectory();
				buildIndex(clusters, config, total, index);
				LOG.info("Creating index took {}ms", watch.elapsed(TimeUnit.MILLISECONDS));
				int collectSimilarSensesPerSense = Integer.parseInt(line.getOptionValue(OPTION_SIMILAR_SENSES));
				writeLuceneBasedSimilarities(output, collectSimilarSensesPerSense, clusters, total, index);
			} else {
				LOG.error("Missing option, provide either " + OPTION_SIMILAR_SENSES + " or " + OPTION_ALL_SIMILARITIES);
			}
		} catch (ParseException e) {
			LOG.error("Invalid arguments", e);
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("Error", e);
		}

	}

	private static void writeAllSimilarities(File output, Map<String, Map<Integer, List<Feature>>> clusters,
			int total) {
		try (BufferedWriter out = Utils.openGzipWriter(output)) {
			int count = 0;
			for (Entry<String, Map<Integer, List<Feature>>> cluster : clusters.entrySet()) {
				String senseWord = cluster.getKey();
				for (Entry<Integer, List<Feature>> sense : cluster.getValue().entrySet()) {
					if (count++ % 100 == 0) {
						LOG.info("Searching similarities for sense {}/{}", count, total);
					}
					Integer senseId = sense.getKey();
					String senseWordId1 = senseWord + "#" + senseId;
					for (Feature f : sense.getValue()) {
						String word = f.getWord();
						Integer wordSenseId = f.getSenseId() == null ? 0 : f.getSenseId();
						String senseWordId2 = word + "#" + wordSenseId;
						double score = f.getWeight();
						String similarity = senseWordId1 + "\t" + senseWordId2 + "\t" + score;
						out.write(similarity);
						out.write("\n");
					}
				}
			}
		} catch (Exception e) {
			LOG.error("Error", e);
		}
	}

	private static void writeLuceneBasedSimilarities(File output, int collectSimilarSensesPerSense,
			Map<String, Map<Integer, List<Feature>>> clusters, int total, Directory index)
					throws InterruptedException, IOException {
		Stopwatch watch = Stopwatch.createStarted();
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
	}

	private static void buildIndex(Map<String, Map<Integer, List<Feature>>> clusters, IndexWriterConfig config,
			int total, RAMDirectory index) throws IOException {
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
	}

	private static Options createOptions() {
		Options options = new Options();
		Option input = Option.builder(OPTION_IN_FILE).argName("file").desc("Path to the csv.gz file with DDT data")
				.hasArg().required().type(String.class).required().build();
		options.addOption(input);
		Option output = Option.builder(OPTION_OUT_FILE).argName("output file").desc("Path to the output file dir")
				.hasArg().required().type(String.class).build();
		options.addOption(output);
		Option similarSenses = Option.builder(OPTION_SIMILAR_SENSES).argName("similar senses")
				.desc("Number of top similar senses per sense").hasArg().type(Integer.class).build();
		options.addOption(similarSenses);
		Option allSimilarities = Option.builder(OPTION_ALL_SIMILARITIES).argName("output all connections")
				.desc("A sense is similar to all of its cluster words senses, this creates a large graph. The "
						+ OPTION_SIMILAR_SENSES + " option is ignored in this case.")
				.build();
		options.addOption(allSimilarities);
		return options;
	}
}
