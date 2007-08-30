package org.red5.server.war;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 *
 * Copyright (c) 2006-2007 by respective authors (see below). All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any later
 * version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

import java.rmi.Naming;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.apache.log4j.Logger;
import org.springframework.web.context.ContextLoader;

/**
 * Entry point from which the server config file is loaded while running within
 * a J2EE application container.
 * 
 * This listener should be registered after Log4jConfigListener in web.xml, if
 * the latter is used.
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SubContextLoaderServlet extends RootContextLoaderServlet {

	private final static long serialVersionUID = 41919712007L;

	// Initialize Logging
	public static Logger logger = Logger
			.getLogger(SubContextLoaderServlet.class.getName());

	private static ServletContext servletContext;

	{
		initRegistry();
	}

	/**
	 * Main entry point for the Red5 Server as a war
	 */
	// Notification that the web application is ready to process requests
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		if (null != servletContext) {
			return;
		}
		System.setProperty("red5.deployment.type", "war");

		servletContext = sce.getServletContext();
		String prefix = servletContext.getRealPath("/");

		long time = System.currentTimeMillis();

		logger.info("RED5 Server subcontext loader");
		logger.debug("Path: " + prefix);

		try {
			String[] configArray = servletContext.getInitParameter(
					ContextLoader.CONFIG_LOCATION_PARAM).split("[,\\s]");
			logger.debug("Config location files: " + configArray.length);

			WebSettings settings = new WebSettings();
			settings.setPath(prefix);
			// prefix the config file paths so they can be found later
			String[] subConfigs = new String[configArray.length];
			for (int s = 0; s < configArray.length; s++) {
				String cfg = "file:/" + prefix + configArray[s];
				logger.debug("Sub config location: " + cfg);
				subConfigs[s] = cfg;
			}
			settings.setConfigs(subConfigs);
			settings.setWebAppKey(servletContext
					.getInitParameter("webAppRootKey"));

			// store this contexts settings in the registry
			IRemotableList remote = null;
			boolean firstReg = false;
			try {
				remote = (IRemotableList) Naming
						.lookup("rmi://localhost:1099/subContextList");
			} catch (Exception e) {
				logger.warn("Lookup failed: " + e.getMessage());
			}
			if (remote == null) {
				remote = new RemotableList();
				firstReg = true;
			}
			logger.debug("Adding child web settings");
			remote.addChild(settings);
			logger.debug("Remote list size: " + remote.numChildren());
			if (firstReg) {
				Naming.bind("rmi://localhost:1099/subContextList", remote);
			} else {
				Naming.rebind("rmi://localhost:1099/subContextList", remote);
			}

			try {
				// check to see if root is already initialized and register
				// directly if it is
				logger.debug("Root context from sub: "
						+ servletContext.getContext("/ROOT"));
				if (servletContext.getContext("/ROOT") != null) {
					RootContextLoaderServlet.registerSubContext(settings,
							servletContext);
				}
			} catch (Exception e) {
				// we dont really care if exceptions are thrown here, an NPE is
				// common if the root app is not yet deployed
				e.printStackTrace();
			}

		} catch (Throwable t) {
			logger.error(t);
		}

		long startupIn = System.currentTimeMillis() - time;
		logger.info("Startup done in: " + startupIn + " ms");

	}

}
