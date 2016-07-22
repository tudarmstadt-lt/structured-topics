package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import de.tudarmstadt.lt.structuredtopics.Utils;

public class ExtractImagesFromCache {

	private static final class NoEdgesFilenameFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String name) {
			return !name.contains("_edges");
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(ExtractImagesFromCache.class);

	private static final String OPTION_API_CACHE = "apiCache";
	private static final String OPTION_OUT_FILE = "out";

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File cacheLocation = new File(cl.getOptionValue(OPTION_API_CACHE));
			if (!cacheLocation.exists()) {
				LOG.error("{} does not exist", cacheLocation.getAbsolutePath());
				return;
			}
			File out = new File(cl.getOptionValue(OPTION_OUT_FILE));
			extractImages(cacheLocation, out);
			LOG.info("Done!");
		} catch (ParseException e) {
			LOG.error("Invalid arguments: {}", e.getMessage());
			StringWriter sw = new StringWriter();
			try (PrintWriter w = new PrintWriter(sw)) {
				new HelpFormatter().printHelp(w, Integer.MAX_VALUE, "application", "", options, 0, 0, "", true);
			}
			LOG.error(sw.toString());
		} catch (Exception e) {
			LOG.error("Error", e);
		}
	}

	private static void extractImages(File apiCache, File output) throws FileNotFoundException, IOException {
		File[] files = apiCache.listFiles(new NoEdgesFilenameFilter());
		int total = files.length;
		int count = 0;
		try (BufferedWriter out = Utils.openWriter(output, false)) {
			for (File f : files) {
				if (count++ % 1000 == 0) {
					LOG.info("Parsing file {}/{}", count, total);
				}
				try (FileReader reader = new FileReader(f)) {
					Set<String> uniqueSenses = new HashSet<>();
					Set<Pair<String, String>> uniqueImages = new HashSet<>();
					Gson gson = new Gson();
					Map json = toMapOrEmpty(gson.fromJson(reader, Object.class));
					String synsetId = f.getName().replace("bn_", "bn:").replace(".json", "");
					List senses = toListOrEmpty(json.get("senses"));
					for (Object o : senses) {
						Map sense = toMapOrEmpty(o);
						Object lemma = sense.get("simpleLemma");
						if (lemma != null && lemma instanceof String) {
							uniqueSenses.add((String) lemma);
						}
					}
					List images = toListOrEmpty(json.get("images"));
					for (Object o : images) {
						Map image = toMapOrEmpty(o);
						String thumbUrl = (String) image.get("thumbUrl");
						String url = (String) image.get("url");
						if (thumbUrl != null || url != null) {
							uniqueImages.add(Pair.of(url, thumbUrl));
						}
					}
					out.write(synsetId + "\t" + StringUtils.join(uniqueSenses, ", ") + "\t"
							+ (uniqueImages.stream()
									.map(x -> StringUtils.defaultString(x.getKey() + ", " + x.getValue()))
									.collect(Collectors.joining(", ")))
							+ "\n");
				} catch (Exception e) {
					LOG.error("Error while parsing file {}", f.getAbsolutePath(), e);
				}
			}
		}
	}

	private static Map toMapOrEmpty(Object o) {
		if (o == null || !(o instanceof Map)) {
			return new HashMap<>();
		} else {
			return (Map) o;
		}
	}

	private static List toListOrEmpty(Object o) {
		if (o == null || !(o instanceof List)) {
			return new ArrayList<>();
		} else {
			return (List) o;
		}
	}

	private static Options createOptions() {
		Options options = new Options();
		Option apiCache = Option.builder(OPTION_API_CACHE).argName("apiCache").desc("Api cache folder").required()
				.hasArg().build();
		options.addOption(apiCache);
		Option out = Option.builder(OPTION_OUT_FILE).argName("out").desc("File where the images will be appended")
				.hasArg().required().build();
		options.addOption(out);
		return options;
	}
}
