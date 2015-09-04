package de.tudarmstadt.lt.structuredtopics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import de.tudarmstadt.lt.structuredtopics.Main.InputMode;

public class Utils {
	public static BufferedReader openReader(File input, InputMode mode) throws IOException {
		InputStream in = new FileInputStream(input);
		if (mode == InputMode.GZ) {
			in = new GZIPInputStream(in);
		}
		// TODO if required, encoding should be passed here
		Reader reader = new InputStreamReader(in);
		return new BufferedReader(reader);
	}

	public static BufferedWriter openWriter(File output) throws IOException {
		OutputStream out = new FileOutputStream(output);
		out = new GZIPOutputStream(out);
		// TODO if required, encoding should be passed here
		Writer writer = new OutputStreamWriter(out);
		return new BufferedWriter(writer);
	}

	public static void main(String[] args) throws Exception {
		String line = null;
		int count = 0;
		String feature = null;
		int counts = 0;
		try (BufferedReader in = new BufferedReader(
				new FileReader(new File("/home/panic/Schreibtisch/Downloads/word-freq-news_no_head_no_blank")))) {
			while ((line = in.readLine()) != null) {
				count++;
				String[] split = line.split("\t");
				feature = split[0];
				counts = Integer.parseInt(split[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("line: " + count + " feature: " + feature + " counts: " + counts);
		}
	}
}
