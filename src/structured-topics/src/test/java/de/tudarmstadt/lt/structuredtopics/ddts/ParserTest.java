package de.tudarmstadt.lt.structuredtopics.ddts;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class ParserTest {

	// separators
	private static final String TAB = "\t";
	private static final String C = ",";
	private static final String CW = ", ";

	private Parser sut;

	@Before
	public void setUp() {
		sut = new Parser();
	}

	@Test
	public void testSingleWordNoWeights() {
		String line = "word#POSTAG" + TAB + "1" + TAB + "word2#POSTAG2" + CW + "word3#POSTAG3";

		SenseCluster actual = sut.parseSenseClusterFromLine(line);

		SenseCluster expected = new SenseCluster(new Sense(newArrayList(new SingleWord("word", "POSTAG")), 1),
				newArrayList(new ClusterWord(newArrayList(new SingleWord("word2", "POSTAG2")), null, null),
						new ClusterWord(newArrayList(new SingleWord("word3", "POSTAG3")), null, null)));

		assertEquals(actual, expected);
	}

	@Test
	public void testSingleWordWithWeights() {
		String line = "word#POSTAG" + TAB + "1" + TAB + "word2#POSTAG2#1:1.000" + C + "word3#POSTAG3#2:0.0667";

		SenseCluster actual = sut.parseSenseClusterFromLine(line);

		SenseCluster expected = new SenseCluster(new Sense(newArrayList(new SingleWord("word", "POSTAG")), 1),
				newArrayList(new ClusterWord(newArrayList(new SingleWord("word2", "POSTAG2")), 1, 1.000),
						new ClusterWord(newArrayList(new SingleWord("word3", "POSTAG3")), 2, 0.0667)));

		assertEquals(actual, expected);
	}

	@Test
	public void testMultiWordWithPos() {
		String line = "wordA#POSTAGa wordB#POSTAGb wordC#POSTAGc" + TAB + "0" + TAB
				+ "word1#POSTAG1 word2#POSTAG2#1:1.000" + C + "word3#POSTAG3#2:0.0667";

		SenseCluster actual = sut.parseSenseClusterFromLine(line);

		SenseCluster expected = new SenseCluster(
				new Sense(
						newArrayList(new SingleWord("wordA", "POSTAGa"), new SingleWord("wordB", "POSTAGb"),
								new SingleWord("wordC", "POSTAGc")),
						0),
				newArrayList(new ClusterWord(
						newArrayList(new SingleWord("word1", "POSTAG1"), new SingleWord("word2", "POSTAG2")), 1, 1.000),
						new ClusterWord(newArrayList(new SingleWord("word3", "POSTAG3")), 2, 0.0667)));

		assertEquals(actual, expected);
	}

	@Test
	public void testMultiWordWithNoPos() {
		String line = "wordA wordB wordC" + TAB + "0" + TAB + "word1 word2#1:1.000" + C + "word3#2:0.0667";

		SenseCluster actual = sut.parseSenseClusterFromLine(line);

		SenseCluster expected = new SenseCluster(
				new Sense(newArrayList(new SingleWord("wordA", null), new SingleWord("wordB", null),
						new SingleWord("wordC", null)), 0),
				newArrayList(new ClusterWord(newArrayList(new SingleWord("word1", null), new SingleWord("word2", null)),
						1, 1.000), new ClusterWord(newArrayList(new SingleWord("word3", null)), 2, 0.0667)));

		assertEquals(actual, expected);
	}

	private void assertEquals(SenseCluster actual, SenseCluster expected) {
		assertThat(actual.getSense().getWords(),
				Matchers.contains(expected.getSense().getWords().toArray(new SingleWord[0])));
		assertThat(actual.getSense().getSenseId(), is(expected.getSense().getSenseId()));

		assertThat(actual.getClusterWords().size(), is(expected.getClusterWords().size()));
		for (int i = 0; i < actual.getClusterWords().size(); i++) {
			ClusterWord cwActual = actual.getClusterWords().get(i);
			ClusterWord cwExpected = expected.getClusterWords().get(i);
			assertThat(cwActual, is(cwExpected));
			assertThat(cwActual.getWords(), Matchers.contains(cwExpected.getWords().toArray(new SingleWord[0])));
		}

	}

}
