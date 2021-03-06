package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import de.tudarmstadt.lt.structuredtopics.Utils;

@Path("/images")
public class ImageLookup {

	private static final Logger LOG = LoggerFactory.getLogger(ImageLookup.class);

	private static Map<String, List<String>> wordImagePaths = Maps.newHashMap();
	private static String basepath;

	@GET
	@Path("/one/{word}")
	@Produces("image/*")
	public Response getOneImagePath(@PathParam("word") String word) throws FileNotFoundException {
		List<String> paths = wordImagePaths.get(word);
		if (paths == null || paths.isEmpty()) {
			return null;
		} else {

			String filename = paths.get(0);
			File file = new File(filename);
			return Response.ok(new FileInputStream(file), new MediaType("image", FilenameUtils.getExtension(filename)))
					.build();
		}
	}

	@GET
	@Path("/index/{word}/{index}")
	@Produces("image/*")
	public Response getIndexImagePath(@PathParam("word") String word, @PathParam("index") Integer index)
			throws FileNotFoundException {
		List<String> paths = wordImagePaths.get(word);
		if (paths == null || paths.isEmpty() || index >= paths.size()) {
			return null;
		} else {

			String filename = paths.get(index);
			File file = new File(filename);
			return Response.ok(new FileInputStream(file), new MediaType("image", FilenameUtils.getExtension(filename)))
					.build();
		}
	}

	@GET
	@Path("/all/{word}")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getAllImagesPath(@PathParam("word") String word) {
		List<String> paths = wordImagePaths.get(word);
		if (paths == null || paths.isEmpty()) {
			return null;
		} else {
			List<String> result = new ArrayList<>();
			for (int i = 0; i < paths.size(); i++) {
				result.add(basepath + "/images/index/" + word + "/" + i);
			}
			return result;
		}
	}

	@GET
	@Path("/index")
	@Produces(MediaType.APPLICATION_JSON)
	public Set<String> getIndex() {
		return Collections.unmodifiableSet(wordImagePaths.keySet());
	}

	public static void loadIndex(File senseImages, String basepath) throws IOException {
		ImageLookup.basepath = basepath;
		LOG.info("Reading index from {}", senseImages.getAbsolutePath());
		try (BufferedReader in = Utils.openReader(senseImages)) {
			String line = null;
			while ((line = in.readLine()) != null) {
				String[] split = line.split("\t");
				if (split.length == 3) {
					String word = split[0];
					String imagePath = split[2];
					List<String> paths = wordImagePaths.get(word);
					if (paths == null) {
						paths = new ArrayList<>();
						wordImagePaths.put(word, paths);
					}
					if (!paths.contains(imagePath)) {
						paths.add(imagePath);
					}
				} else {
					LOG.warn("Invalid line format {}", line);
				}
			}
		}
		LOG.info("Reading index done, contains {} words", wordImagePaths.size());
	}
}