package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import de.tudarmstadt.lt.structuredtopics.Utils;

public class DownloadImages {
	private static final String OPTION_DOWNLOAD_LIST = "downloadList";
	private static final String OPTION_TARGET_DIR = "downloadTo";
	private static final String OPTION_DOWNLOAD_INDEX = "index";
	private static final int IO_TIMEOUT = (int) TimeUnit.SECONDS.toMillis(30);

	private static final Logger LOG = LoggerFactory.getLogger(DownloadImages.class);

	public static void main(String[] args) {
		Options options = createOptions();
		try {
			CommandLine cl = new DefaultParser().parse(options, args, true);
			File downloadList = new File(cl.getOptionValue(OPTION_DOWNLOAD_LIST));
			if (!downloadList.exists()) {
				LOG.error("{} does not exist", downloadList.getAbsolutePath());
				return;
			}
			File out = new File(cl.getOptionValue(OPTION_TARGET_DIR));
			if (!out.exists()) {
				out.mkdir();
			}
			File index = new File(cl.getOptionValue(OPTION_DOWNLOAD_INDEX));
			LOG.info("Downloading images from file {} to directory {} with index {}", downloadList.getAbsolutePath(),
					out.getAbsolutePath(), index.getAbsolutePath());
			downloadImages(downloadList, out, index);
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

	private static void downloadImages(File downloadList, File out, File index) {
		try (BufferedReader in = Utils.openReader(downloadList)) {
			try (BufferedWriter indexOut = Utils.openWriter(index)) {
				in.lines().parallel().forEach(line -> {
					String[] split = line.split("\\t");
					if (split.length != 3) {
						return;
					}
					String synsetId = split[0];
					if (StringUtils.isBlank(synsetId)) {
						return;
					}
					String[] uniqueSenses = split[1].split(", ");
					String[] imageUrls = split[2].split(", ");
					for (String imageUrl : imageUrls) {
						try {
							String trim = imageUrl.trim();
							URL url = new URL(trim);
							Hasher hash = Hashing.murmur3_32(42).newHasher();
							hash.putString(trim, Charsets.UTF_8);
							String imageHash = hash.hash().toString();
							String extension = "";
							int lastIndexOfDot = trim.lastIndexOf(".");
							if (lastIndexOfDot != -1) {
								extension = trim.substring(lastIndexOfDot, trim.length());
							} else {
								continue;
							}
							File imageFile = new File(out, synsetId + "_" + imageHash + extension);
							if (imageFile.exists()) {
								LOG.info("{} exists, skipping {}", imageFile.getAbsolutePath(), url);
							} else {
								LOG.info("Downloading {} to {}", url, imageFile.getAbsolutePath());
								URLConnection connection = url.openConnection();
								connection.setConnectTimeout(IO_TIMEOUT);
								connection.setReadTimeout(IO_TIMEOUT);
								try (InputStream imageIn = connection.getInputStream()) {
									Files.copy(imageIn, imageFile.toPath());
								}
							}
							if (imageFile.length() == 0) {
								LOG.info(
										"Image {} has been downloaded but size is 0, deleting it and trying next image from list",
										imageFile.getAbsolutePath());
								imageFile.delete();
								continue;
							}
							// everything worked, save to index and skip other
							// images
							for (String sense : uniqueSenses) {
								indexOut.write(sense + "\t" + synsetId + "\t" + imageFile.getAbsolutePath() + "\n");
							}
							break;
						} catch (MalformedURLException e) {
							// ignore
						} catch (FileNotFoundException e) {
							LOG.error("Image {} does not exist", imageUrl, e);
						} catch (Exception e) {
							LOG.error("Error while downloading image Url {}", imageUrl, e);
							try {
								Thread.sleep(TimeUnit.MINUTES.toMillis(1));
							} catch (InterruptedException e1) {
								// ignore
							}
						}
					}
				});
			}
		} catch (IOException e) {
			LOG.error("Error", e);
		}

	}

	private static Options createOptions() {
		Options options = new Options();
		Option apiCache = Option.builder(OPTION_TARGET_DIR).argName("targetDir")
				.desc("Target directory, where the images will be saved").required().hasArg().build();
		options.addOption(apiCache);
		Option index = Option.builder(OPTION_DOWNLOAD_INDEX).argName("index").desc("Index, where image urls are stored")
				.required().hasArg().build();
		options.addOption(index);
		Option downloadList = Option.builder(OPTION_DOWNLOAD_LIST).argName("outDownload")
				.desc("Flat file with a list of all images for each synset").hasArg().required().build();
		options.addOption(downloadList);
		return options;
	}
}
