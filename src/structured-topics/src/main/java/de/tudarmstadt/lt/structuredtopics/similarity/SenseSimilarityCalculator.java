package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
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

import de.tudarmstadt.lt.structuredtopics.Utils;
import de.tudarmstadt.lt.structuredtopics.ddts.ClusterWord;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser;
import de.tudarmstadt.lt.structuredtopics.ddts.Sense;
import de.tudarmstadt.lt.structuredtopics.ddts.SenseCluster;

public class SenseSimilarityCalculator {

	private static final String OPTION_ALL_SIMILARITIES = "ALL";
	private static final String OPTION_SIMILAR_SENSES = "N";
	private static final String OPTION_OUT_FILE = "out";
	private static final String OPTION_IN_FILE = "in";
	private static final String OPTION_FILTER_POS_TAG = "filterpos";
	private static final String OPTION_FILTER_REGEX = "filterregex";
	private static final Logger LOG = LoggerFactory.getLogger(SenseSimilarityCalculator.class);

	public static final String WORD_REGEX = ".*[a-zA-Z]+.*";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine line = new DefaultParser().parse(options, args, true);
			File ddt = new File(line.getOptionValue(OPTION_IN_FILE));
			File output = new File(line.getOptionValue(OPTION_OUT_FILE));
			Parser parser = new Parser();
			List<SenseCluster> clusters = parser.parseDDT(ddt);
			if (line.hasOption(OPTION_FILTER_POS_TAG)) {
				LOG.info("Filtering by pos-tag");
				Utils.filterClustersByPosTag(clusters);
			}
			if (line.hasOption(OPTION_FILTER_REGEX)) {
				LOG.info("Filtering by regex");
				Utils.filterClustersByRegEx(clusters, WORD_REGEX);
			}
			Analyzer analyzer = new KeywordAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			int total = clusters.size();
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

	private static void writeAllSimilarities(File output, List<SenseCluster> clusters, int total) {
		try (BufferedWriter out = Utils.openGzipWriter(output)) {
			int count = 0;
			for (SenseCluster cluster : clusters) {
				Sense sense = cluster.getSense();
				String senseWord = sense.getFullWord();
				if (count++ % 100 == 0) {
					LOG.info("Searching similarities for sense {}/{}", count, total);
				}
				Integer senseId = sense.getSenseId();
				String senseWordId1 = senseWord + "#" + senseId;
				for (ClusterWord clusterWord : cluster.getClusterWords()) {
					String word = clusterWord.getFullWord();
					if (clusterWord.getRelatedSenseId() != null) {
						Integer wordSenseId = clusterWord.getRelatedSenseId();
						String senseWordId2 = word + "#" + wordSenseId;
						double score = clusterWord.getWeight() == null ? 1 : clusterWord.getWeight();
						String similarity = senseWordId1 + "\t" + senseWordId2 + "\t" + score;
						out.write(similarity);
						out.write("\n");
					} else {
						// special handling for clusters without sense ids, in
						// this case the sense is equal to all other senses with
						// the same id
						for (Integer id : findSenseIdsWithSameFullWord(clusters, word)) {
							Integer wordSenseId = id;
							String senseWordId2 = word + "#" + wordSenseId;
							double score = clusterWord.getWeight() == null ? 1 : clusterWord.getWeight();
							String similarity = senseWordId1 + "\t" + senseWordId2 + "\t" + score;
							out.write(similarity);
							out.write("\n");
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.error("Error", e);
		}
	}

	private static List<Integer> findSenseIdsWithSameFullWord(List<SenseCluster> clusters, String fullWord) {
		List<Integer> ids = new ArrayList<>();
		for (SenseCluster cluster : clusters) {
			if (cluster.getSense().getFullWord().equals(fullWord)) {
				ids.add(cluster.getSense().getSenseId());
			}
		}
		return ids;
	}

	private static void writeLuceneBasedSimilarities(File output, int collectSimilarSensesPerSense,
			List<SenseCluster> clusters, int total, Directory index) throws InterruptedException, IOException {
		Stopwatch watch = Stopwatch.createStarted();
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		CountDownLatch latch = new CountDownLatch(clusters.size());
		try (BufferedWriter out = Utils.openGzipWriter(output)) {
			AtomicInteger count = new AtomicInteger();
			clusters.parallelStream().forEach(cluster -> {
				try {
					String senseWord = cluster.getSense().getFullWord();
					if (count.incrementAndGet() % 100 == 0) {
						LOG.info("Searching similarities for sense {}/{}", count, total);
					}
					Integer senseId = cluster.getSense().getSenseId();
					String senseWordId1 = senseWord + "#" + senseId;
					BooleanQuery.Builder builder = new BooleanQuery.Builder();
					BooleanQuery.setMaxClauseCount(1000000);
					for (ClusterWord clusterWord : cluster.getClusterWords()) {
						String word = clusterWord.getFullWord();
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

	private static void buildIndex(List<SenseCluster> clusters, IndexWriterConfig config, int total, RAMDirectory index)
			throws IOException {
		try (IndexWriter w = new IndexWriter(index, config)) {
			int count = 0;
			for (SenseCluster cluster : clusters) {
				String senseWord = cluster.getSense().getFullWord();
				Document senseDocument = new Document();
				if (count++ % 100 == 0) {
					LOG.info("indexing sense {}/{}, index size: {} bytes", count, total, index.ramBytesUsed());
				}
				Integer senseId = cluster.getSense().getSenseId();
				senseDocument.add(new StringField("sense_word_id", senseWord + "#" + senseId, Store.YES));
				for (ClusterWord clusterWord : cluster.getClusterWords()) {
					String word = clusterWord.getFullWord();
					senseDocument.add(new StringField("sense_cluster_word", word, Store.NO));
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

		Option filterPosTag = Option.builder(OPTION_FILTER_POS_TAG).argName("filter by pos-tag")
				.desc("Filters all senses by pos-tag (NN, NP or JJ)").build();
		options.addOption(filterPosTag);

		Option filterRegex = Option.builder(OPTION_FILTER_REGEX).argName("filter by regex")
				.desc("Filters all senses by the regular expression " + WORD_REGEX).build();
		options.addOption(filterRegex);
		return options;
	}
}
