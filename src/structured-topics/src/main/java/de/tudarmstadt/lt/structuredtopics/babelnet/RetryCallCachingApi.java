package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

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

	private int lastApiCallCount = 0;

	@Override
	protected String callApi(String method, Map<String, String> parameters) {
		String result = null;
		while (result == null) {
			try {
				result = super.callApi(method, parameters);
			} catch (KeyLimitReachedException e) {
				LOG.warn("Key limit reached, waiting... ({} api calls before)", getApiCallCount() - lastApiCallCount);
				lastApiCallCount = getApiCallCount();
				sleep(1, TimeUnit.HOURS);
			} catch (IOException e) {
				LOG.warn("Connection error", e);
				return null;
			}
		}
		return result;
	}

	private static void sleep(long duration, TimeUnit unit) {
		try {
			Thread.sleep(unit.toMillis(duration));
		} catch (InterruptedException e) {
			Throwables.propagate(e);
		}
	}
}
