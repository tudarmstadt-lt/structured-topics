package de.tudarmstadt.lt.structuredtopics.babelnet;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CrawlerTest {

	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { { "java#n#1", "java" }, { "Motif_(software)", "Motif" },
				{ "source_code", "source_code" }, { "#tweet", "#tweet" } });
	}

	@Parameter
	public String sense;
	@Parameter(value = 1)
	public String expected;

	@Test
	public void testSenseCleaning() {
		assertThat(Crawler.clean(sense), is(expected));
	}
}
