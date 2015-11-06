package de.tudarmstadt.lt.structuredtopics;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.common.collect.Maps;

public class UtilsTest {

	@Test
	public void testPosTagFiltering() {
		Map<String, Map<Integer, List<Feature>>> clusters = Maps.newHashMap();
		String word1 = "Foo#NN";
		String word2 = "BarBar#VB";
		String word3 = "BarBazFoo#NN";
		String feature1 = "Bar#JJ";
		String feature2 = "Baz#NP";
		String feature3 = "FooBar#CD";

		Map<Integer, List<Feature>> word1Senses = Maps.newHashMap();
		// word1 is valid (NN) and feature 2 is valid (NP) -> keep sense 0
		word1Senses.put(0, newArrayList(feature(feature1), feature(feature2), feature(feature3)));
		// word1 is valid (NN) but feature 1 is not (CD) -> remove sense 1
		// (empty cluster list)
		word1Senses.put(1, newArrayList(feature(feature3)));
		clusters.put(word1, word1Senses);

		Map<Integer, List<Feature>> word2Senses = Maps.newHashMap();
		// word2 is invalid(VB) but feature 2 is valid (NP) -> remove word2
		// (invalid word)
		word2Senses.put(0, newArrayList(feature(feature2)));
		clusters.put(word2, word2Senses);

		Map<Integer, List<Feature>> word3Senses = Maps.newHashMap();
		// word 3 is valid (NN) but feature 3 is invalid (CD) -> remove word3
		// (empty cluster list)
		word3Senses.put(0, newArrayList(feature(feature3)));
		clusters.put(word3, word3Senses);

		Utils.filterClustersByPosTag(clusters);

		assertThat(clusters, hasKey(word1));
		assertThat(clusters, not(hasKey(word2)));
		assertThat(clusters, not(hasKey(word3)));
	}

	private Feature feature(String word) {
		return new Feature(word, 1.0);
	}
}
