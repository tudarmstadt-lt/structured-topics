import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;
import de.tudarmstadt.lt.structuredtopics.Utils;
import de.tudarmstadt.lt.structuredtopics.convert.WordFrequencyConverter;

public class ConverterTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void testConverter() throws IOException {

		// word cid cluster isas
		// Foo#NP 0 Bar, Baz
		// Bar#VP 0 Foo, Baz
		// Bar#NP 1 Baz
		// Baz#NP 0 Foo, Bar
		String arg0 = new File("src/test/resources/ddt.csv.gz").getAbsolutePath();
		// word freq
		// Foo 3
		// Bar 5
		// Baz 7
		String arg1 = new File("src/test/resources/word-freq.csv.gz").toString();
		File out = tmp.newFolder();
		String arg2 = out.getAbsolutePath();
		WordFrequencyConverter.main(new String[] { arg0, arg1, arg2 });

		File senseCounts = new File(out, "senseCounts.gz");
		File senseClusterWordCounts = new File(out, "senseClusterWordCounts.gz");
		assertTrue(senseCounts.exists());
		assertTrue(senseClusterWordCounts.exists());

		// each sense should sum up the frequencies of the cluster words
		String expected = "Foo#NP#0	12\n" + "Bar#NP#1	7\n" + "Bar#VP#0	10\n" + "Baz#NP#0	8\n";
		assertThat(readGzFile(senseCounts), is(expected));

		// sense - cluster word frequency is assumed to be 10
		String expected2 = "Foo#NP#0	Bar	10\n" + "Foo#NP#0	Baz	10\n" + "Bar#NP#1	Baz	10\n"
				+ "Bar#VP#0	Foo	10\n" + "Bar#VP#0	Baz	10\n" + "Baz#NP#0	Foo	10\n" + "Baz#NP#0	Bar	10\n";
		assertThat(readGzFile(senseClusterWordCounts), is(expected2));
	}

	private String readGzFile(File f) {
		StringBuilder b = new StringBuilder();
		try (BufferedReader in = Utils.openReader(f, InputMode.GZ)) {
			String line = null;
			while ((line = in.readLine()) != null) {
				b.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return b.toString();
	}

}
