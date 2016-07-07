package de.tudarmstadt.lt.structuredtopics.babelnet;

import java.io.File;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageServer {

	private static final Logger LOG = LoggerFactory.getLogger(ImageServer.class);

	public static void main(String[] args) {
		Server jettyServer = null;
		try {
			if (args.length != 3) {
				LOG.warn("Expected parameters missing: <port> <path to senseImages file> <basepath>");
				LOG.warn("Sense Images Format: <word TAB synsetId TAB pathToImage>");
				return;
			}
			int port = Integer.parseInt(args[0]);
			File senseImages = new File(args[1]);
			if (!senseImages.exists()) {
				LOG.error("File {} not found", senseImages.getAbsolutePath());
				return;
			}
			String basepath = args[2];
			ImageLookup.loadIndex(senseImages, basepath);
			jettyServer = new Server(port);
			ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
			context.setContextPath("/");

			jettyServer.setHandler(context);

			ServletHolder jerseyServlet = context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class, "/*");
			jerseyServlet.setInitOrder(0);

			jerseyServlet.setInitParameter("jersey.config.server.provider.classnames",
					ImageLookup.class.getCanonicalName());

			jettyServer.start();
			jettyServer.join();
		} catch (Exception e) {
			LOG.error("Error", e);
		} finally {
			if (jettyServer != null) {
				jettyServer.destroy();
			}
		}
	}
}
