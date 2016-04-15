package de.tudarmstadt.lt.structuredtopics.similarity;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class SenseSimilarityCalculatorTest {

	// separators
	private static final String TAB = "\t";
	private static final String C = ",";
	private static final String CW = ", ";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testMultiWordWithPosFormat() throws IOException {
		File ddt = folder.newFile("ddt.csv");
		String lines = "wordA#POSTAGa wordB#POSTAGb wordC#POSTAGc" + TAB + "0" + TAB
				+ "word1#POSTAG1 word2#POSTAG2#1:1.000" + C + "word3#POSTAG3#2:0.0667";
		Files.write(lines, ddt, Charsets.UTF_8);
		File similaritiesFile = folder.newFile("sim.csv");

		SenseSimilarityCalculator.writeAllSimilarities(similaritiesFile, ddt);

		List<String> expectedSimilarities = Lists.newArrayList("wordA wordB wordC#0	word1 word2#1	1.0",
				"wordA wordB wordC#0	word3#2	0.0667");

		List<String> similarities = Files.readLines(similaritiesFile, Charsets.UTF_8);
		assertThat(similarities, is(expectedSimilarities));
	}

}
