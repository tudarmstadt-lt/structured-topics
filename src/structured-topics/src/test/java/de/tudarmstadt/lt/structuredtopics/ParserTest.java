package de.tudarmstadt.lt.structuredtopics;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Maps;

public class ParserTest {

	private Parser sut;

	@Before
	public void setUp() {
		sut = new Parser();
	}

	@Test
	public void testInputFormat1() {
		String line = "Root#NP\t0\tConstruction#NP#1:1.000,Stock#NP#3:0.796,Control#NP#1:0.696,Services#NP#1:0.633,Service#NP#0:0.588,Fund#NP#0:0.554,Care#NP#0:0.526";
		int lineNumber = 42;
		Map<String, Map<Integer, List<Feature>>> senseClusterWords = Maps.newHashMap();
		sut.addClusterFromLine(line, lineNumber, senseClusterWords);

		assertThat(senseClusterWords.size(), is(1));
		Map<Integer, List<Feature>> cluster = senseClusterWords.get("Root#NP");
		assertThat(cluster.size(), is(1));
		List<Feature> features = cluster.get(0);
		assertThat(features, hasItem(new Feature("Construction#NP", 1)));
		assertThat(features, hasItem(new Feature("Stock#NP", 0.796)));
		assertThat(features, hasItem(new Feature("Control#NP", 0.696)));
		assertThat(features, hasItem(new Feature("Services#NP", 0.633)));
		assertThat(features, hasItem(new Feature("Service#NP", 0.588)));
		assertThat(features, hasItem(new Feature("Fund#NP", 0.554)));
		assertThat(features, hasItem(new Feature("Care#NP", 0.526)));
	}

	@Test
	public void testInputFormat2() {
		String line = "java#NN\t0\tOpenGL#NP, Windows#NP, EE#NP, SDK#NP, Linux#NP, Desktop#NP";
		int lineNumber = 42;
		Map<String, Map<Integer, List<Feature>>> senseClusterWords = Maps.newHashMap();
		sut.addClusterFromLine(line, lineNumber, senseClusterWords);

		assertThat(senseClusterWords.size(), is(1));
		Map<Integer, List<Feature>> cluster = senseClusterWords.get("java#NN");
		assertThat(cluster.size(), is(1));
		List<Feature> features = cluster.get(0);
		features.stream().forEach(x -> System.out.println(x));
		assertThat(features, hasItem(new Feature("OpenGL#NP", 1)));
		System.out.println(features.get(1).equals(new Feature("Windows#NP", 0.8571428571428571)));
		assertThat(features, hasItem(new Feature("Windows#NP", 0.8571428571428571)));
		assertThat(features, hasItem(new Feature("EE#NP", 0.7142857142857143)));
		assertThat(features, hasItem(new Feature("SDK#NP", 0.5714285714285714)));
		assertThat(features, hasItem(new Feature("Linux#NP", 0.42857142857142855)));
		assertThat(features, hasItem(new Feature("Desktop#NP", 0.2857142857142857)));
	}
}
