package de.tudarmstadt.lt.structuredtopics.similarity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;

import de.tudarmstadt.lt.structuredtopics.Utils;
import de.tudarmstadt.lt.structuredtopics.ddts.ClusterWord;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser;
import de.tudarmstadt.lt.structuredtopics.ddts.Parser.DDTIterator;
import de.tudarmstadt.lt.structuredtopics.ddts.Sense;
import de.tudarmstadt.lt.structuredtopics.ddts.SenseCluster;

public class SenseSimilarityCalculator {

	private static final String OPTION_ALL_SIMILARITIES = "ALL";
	private static final String OPTION_SIMILAR_SENSES = "N";
	private static final String OPTION_OUT_FILE = "out";
	private static final String OPTION_IN_FILE = "in";
	private static final Logger LOG = LoggerFactory.getLogger(SenseSimilarityCalculator.class);

	public static final String WORD_REGEX = ".*[a-zA-Z]+.*";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			Stopwatch watch = Stopwatch.createStarted();
			CommandLine line = new DefaultParser().parse(options, args, true);
			File ddt = new File(line.getOptionValue(OPTION_IN_FILE));
			File output = new File(line.getOptionValue(OPTION_OUT_FILE));
			Parser parser = new Parser();
			Analyzer analyzer = new KeywordAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			if (line.hasOption(OPTION_ALL_SIMILARITIES)) {
				LOG.info("Calculating all similarities");
				writeAllSimilarities(output, ddt);
			} else if (line.hasOption(OPTION_SIMILAR_SENSES)) {
				LOG.info("Calculating similarities using index");
				Stopwatch watch2 = Stopwatch.createStarted();
				LOG.info("Starting indexing");
				RAMDirectory index = new RAMDirectory();
				buildIndex(ddt, config, index);
				LOG.info("Creating index took {}ms", watch2.elapsed(TimeUnit.MILLISECONDS));
				int collectSimilarSensesPerSense = Integer.parseInt(line.getOptionValue(OPTION_SIMILAR_SENSES));
				writeLuceneBasedSimilarities(output, collectSimilarSensesPerSense, ddt, index);
			} else {
				LOG.error("Missing option, provide either " + OPTION_SIMILAR_SENSES + " or " + OPTION_ALL_SIMILARITIES);
			}
			LOG.info("Done, results available at {}, time: {}s", output.getAbsolutePath(),
					watch.elapsed(TimeUnit.SECONDS));
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

	@VisibleForTesting
	protected static void writeAllSimilarities(File output, File ddt) {
		int total = Utils.countLines(ddt);
		try (BufferedWriter out = Utils.openWriter(output, false)) {
			int count = 0;
			try (DDTIterator it = new Parser().iterateDDT(ddt)) {
				while (it.hasNext()) {
					SenseCluster cluster = it.next();
					if (cluster == null) {
						continue;
					}
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
							// special handling for clusters without sense ids,
							// in this case the sense is equal to all other
							// senses with the same id
							for (Integer id : findSenseIdsWithSameFullWord(ddt, word, total)) {
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
			}
		} catch (Exception e) {
			LOG.error("Error", e);
		}
	}

	private static Map<String, List<Integer>> fullWordSenseIdIndex;
	private static List<Integer> NOT_INDEXED = new ArrayList<>();
	static {
		NOT_INDEXED.add(0);
	}

	private static List<Integer> findSenseIdsWithSameFullWord(File ddt, String fullWord, int total) {
		if (fullWordSenseIdIndex == null) {
			LOG.info(
					"Building full word sense index for {} clusters to generate all similarities without sense ids in cluster words",
					total);
			Stopwatch watch = Stopwatch.createStarted();
			fullWordSenseIdIndex = new HashMap<>(total / 4);
			try (DDTIterator it = new Parser().iterateDDT(ddt)) {
				while (it.hasNext()) {
					SenseCluster cluster = it.next();
					if (cluster == null) {
						continue;
					}
					String senseFullWord = cluster.getSense().getFullWord();
					Integer senseId = cluster.getSense().getSenseId();
					List<Integer> ids = fullWordSenseIdIndex.get(senseFullWord);
					if (ids == null) {
						ids = new ArrayList<>(4);
						fullWordSenseIdIndex.put(senseFullWord, ids);
					}
					ids.add(senseId);
				}
			} catch (IOException e) {
				LOG.error("Error", e);
			}
			LOG.info("Full index created in {}s", watch.elapsed(TimeUnit.SECONDS));
		}
		List<Integer> ids = fullWordSenseIdIndex.get(fullWord);
		if (ids != null) {
			return ids;
		} else {
			// in case the cluster words contain a sense which is not included
			// in the senses
			return NOT_INDEXED;
		}
	}

	@VisibleForTesting
	protected static void writeLuceneBasedSimilarities(File output, int collectSimilarSensesPerSense, File ddt,
			Directory index) throws InterruptedException, IOException {
		int total = Utils.countLines(ddt);
		Stopwatch watch = Stopwatch.createStarted();
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		CountDownLatch latch = new CountDownLatch(total);
		try (BufferedWriter out = Utils.openWriter(output, false)) {
			AtomicInteger count = new AtomicInteger();
			try (DDTIterator iterator = new Parser().iterateDDT(ddt)) {
				StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), true)
						.forEach(cluster -> {
							try {
								if (cluster != null) {
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
										String senseWordId2 = reader.document(s.doc).getField("sense_word_id")
												.stringValue();
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
			}
			// don't close the stream before all work is done
			latch.await();
			LOG.info("Searching similarities took {}ms", watch.elapsed(TimeUnit.MILLISECONDS));
			LOG.info("Done");
		}
	}

	private static void buildIndex(File ddt, IndexWriterConfig config, RAMDirectory index) throws IOException {
		int total = Utils.countLines(ddt);
		try (IndexWriter w = new IndexWriter(index, config)) {
			int count = 0;
			try (DDTIterator it = new Parser().iterateDDT(ddt)) {
				while (it.hasNext()) {
					SenseCluster cluster = it.next();
					if (cluster == null) {
						continue;
					}
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
