package de.tudarmstadt.lt.structuredtopics.babelnet;

import static org.apache.commons.codec.Charsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

/**
 * Uses the rest api or an internal cache if a resource is known.
 *
 */
public class CachingApi {

	public static class KeyLimitReachedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

	private static final String API = "babelnet.io/v3/";
	private static final String API_GET_SYNSET = "getSynset";
	private static final String API_GET_EDGES = "getEdges";
	private static final String API_GET_SENSES = "getSenses";
	private static final int MS_BETWEEN_REQUEST = 500;
	private static final Logger LOG = LoggerFactory.getLogger(CachingApi.class);

	private File cacheLocation;
	private String apiKey;
	private int apiCallCount = 0;

	public CachingApi(File cacheLocation, String apiKey) {
		this.cacheLocation = cacheLocation;
		if (!cacheLocation.exists()) {
			cacheLocation.mkdirs();
		}
		this.apiKey = apiKey;
	}

	/**
	 * 
	 * @param synsetId
	 *            id of the synset
	 * @return The JSON-Response of the getSynset Api call
	 * @throws KeyLimitReachedException
	 *             if the key is invalid or the key limit is reached
	 * @throws IOException
	 *             if any io-error occurs
	 */
	public String getSynset(String synsetId) throws KeyLimitReachedException, IOException {
		File cachedResult = new File(cacheLocation, synsetId.replace(":", "_") + ".json");
		if (cachedResult.exists()) {
			String result = Files.toString(cachedResult, UTF_8);
			if (StringUtils.isEmpty(result)) {
				LOG.error("Empty result in file {}, deleting file from cache", cachedResult.getAbsolutePath());
				cachedResult.delete();
			}
			return result;
		} else {
			Map<String, String> parameters = Maps.newHashMap();
			parameters.put("id", synsetId);
			String result = callApi(API_GET_SYNSET, parameters);
			Files.write(result, cachedResult, UTF_8);
			return result;
		}
	}

	/**
	 * 
	 * @param word
	 *            any word
	 * @return The JSON-Response of the getSenses Api call
	 * @throws KeyLimitReachedException
	 *             if the key is invalid or the key limit is reached
	 * @throws IOException
	 *             if any io-error occurs
	 */
	public String getSenses(String word) throws KeyLimitReachedException, IOException {
		File cachedResult = new File(cacheLocation, "word_" + URLEncoder.encode(word, "UTF-8") + ".json");
		if (cachedResult.exists()) {
			String result = Files.toString(cachedResult, UTF_8);
			if (StringUtils.isEmpty(result)) {
				LOG.error("Empty result in file {}, deleting file from cache", cachedResult.getAbsolutePath());
				cachedResult.delete();
			}
			return result;
		} else {
			Map<String, String> parameters = Maps.newHashMap();
			parameters.put("word", word);
			// TODO multi language support?
			parameters.put("lang", "EN");
			String result = callApi(API_GET_SENSES, parameters);
			Files.write(result, cachedResult, UTF_8);
			return result;
		}
	}

	/**
	 * 
	 * @param synsetId
	 *            id of the synset
	 * @return The JSON-Response of the getEdges Api call
	 * @throws KeyLimitReachedException
	 *             if the key is invalid or the key limit is reached
	 * @throws IOException
	 *             if any io-error occurs
	 */
	public String getEdges(String synsetId) throws KeyLimitReachedException, IOException {
		File cachedResult = new File(cacheLocation, synsetId.replace(":", "_") + "_edges.json");
		if (cachedResult.exists()) {
			String result = Files.toString(cachedResult, UTF_8);
			if (StringUtils.isEmpty(result)) {
				LOG.error("Empty result in file {}, deleting file from cache", cachedResult.getAbsolutePath());
				cachedResult.delete();
			}
			return result;
		} else {
			Map<String, String> parameters = Maps.newHashMap();
			parameters.put("id", synsetId);
			String result = callApi(API_GET_EDGES, parameters);
			Files.write(result, cachedResult, UTF_8);
			return result;
		}
	}

	/**
	 *
	 * @return Number of real api calls (times the key quota will be required)
	 */
	public int getApiCallCount() {
		return apiCallCount;
	}

	/**
	 * Makes a call to the api
	 * 
	 * @param method
	 *            the api method to call
	 * @param parameters
	 *            parameters to pass (name-argument)
	 * @return the json-response
	 * @throws KeyLimitReachedException
	 *             if the key limit is reached
	 * @throws IOException
	 *             if any io-error occurs
	 */
	protected String callApi(String method, Map<String, String> parameters)
			throws KeyLimitReachedException, IOException {
		String responseToString = "";
		apiCallCount++;
		try (CloseableHttpClient client = newClosableHttpClient()) {
			// wait between requests
			try {
				Thread.sleep(MS_BETWEEN_REQUEST);
			} catch (InterruptedException e1) {
				// ignore
			}
			URIBuilder getSynsetBuilder = new URIBuilder().setScheme("http").setHost(API).setPath(method)
					.addParameter("key", apiKey);
			for (Entry<String, String> e : parameters.entrySet()) {
				getSynsetBuilder.addParameter(e.getKey(), e.getValue());
			}
			URI getSynsetUri = getSynsetBuilder.build();
			HttpGet get = new HttpGet(getSynsetUri);
			get.addHeader("Accept-Encoding", "gzip");
			HttpResponse response = client.execute(get);
			if (response.getStatusLine().getStatusCode() == 400) {
				LOG.error("Bad request: {}", response.toString());
				throw new IOException("Status Code 400 " + response.toString());
			}
			responseToString = responseToString(response);
		} catch (URISyntaxException e) {
			// internal error
			Throwables.propagate(e);
		}
		if (responseToString.contains("Your key is not valid or the daily requests limit has been reached")) {
			throw new KeyLimitReachedException();
		}
		return responseToString;
	}

	private static CloseableHttpClient newClosableHttpClient() {
		return HttpClientBuilder.create().setDefaultRequestConfig(
				RequestConfig.custom().setConnectTimeout(10000).setSocketTimeout(10000).build()).build();
	}

	private static String responseToString(HttpResponse response) throws UnsupportedOperationException, IOException {
		try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
			StringBuffer resultString = new StringBuffer();
			String line = null;
			while ((line = rd.readLine()) != null) {
				resultString.append(line);
			}
			return resultString.toString();
		}
	}
}
