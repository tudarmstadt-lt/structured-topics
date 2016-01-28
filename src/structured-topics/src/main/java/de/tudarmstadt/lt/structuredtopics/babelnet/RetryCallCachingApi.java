package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API extension which waits one hour if a {@link KeyLimitReachedException} is
 * thrown and then retries the call.
 *
 */
public class RetryCallCachingApi extends CachingApi {

	private static final Logger LOG = LoggerFactory.getLogger(RetryCallCachingApi.class);

	public RetryCallCachingApi(File cacheLocation, String apiKey) {
		super(cacheLocation, apiKey);
	}

	@Override
	protected String callApi(String method, Map<String, String> parameters) throws IOException {
		String result = null;
		while (result == null) {
			try {
				result = super.callApi(method, parameters);
			} catch (KeyLimitReachedException e) {
				LOG.warn("Key limit reached, waiting...");
				try {
					Thread.sleep(TimeUnit.HOURS.toMillis(1));
				} catch (InterruptedException e1) {
					// ignore
				}
			}
		}
		return result;
	}
}
