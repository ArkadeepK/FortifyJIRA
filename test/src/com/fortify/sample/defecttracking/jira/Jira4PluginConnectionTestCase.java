/*
 * (C) Copyright 2015 Hewlett-Packard Development Company, L.P.
 */

package com.fortify.sample.defecttracking.jira;

import java.io.*;
import java.rmi.*;
import java.util.*;

import org.apache.commons.lang.*;
import org.apache.commons.logging.*;
import org.junit.*;

import com.fortify.pub.bugtracker.support.*;

public class Jira4PluginConnectionTestCase {

	private static final Log LOG = LogFactory.getLog(Jira4PluginConnectionTestCase.class);
	private static final String JIRA_URL;
	private static final String JIRA_USERNAME;
	private static final String JIRA_PASSWORD;

	static {
		final Properties properties = new Properties();
		try {
			properties.load(Jira4PluginConnectionTestCase.class.getResourceAsStream("Jira4Tests.properties"));
		} catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		JIRA_URL = properties.getProperty("jira.url");
		JIRA_USERNAME = properties.getProperty("jira.username");
		JIRA_PASSWORD = properties.getProperty("jira.password");
	}

	private static Jira4PluginConnection _connection;

	@AfterClass
	public static void afterClass() throws Exception {
		_connection.closeJiraConnection();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		_connection = new Jira4PluginConnection(JIRA_USERNAME, JIRA_PASSWORD,
				JIRA_URL);
	}

	private BugParam findParam(String identifier, List<BugParam> params) {
		BugParam retval = null;
		for (final BugParam param : params) {
			if (identifier.equals(param.getIdentifier())) {
				retval = param;
				break;
			}
		}
		return retval;
	}

	@Test
	public void testListProjects() throws RemoteException {
		final List<String> projects = _connection.getProjectKeys();
		LOG.debug("Found JIRA projects: " + StringUtils.join(projects, ", "));
		Assert.assertFalse("No projects visible", projects.isEmpty());
	}

	@Test
	public void testPriorities() throws RemoteException {
		final List<String> priorities = _connection.getPriorityNames();

		final List<String> expected = Arrays.asList("Blocker", "Critical",
				"Major", "Minor", "Trivial");
		Assert.assertEquals("Missing or too many priorities found",
				expected.size(), priorities.size());

		Assert.assertTrue("JIRA default priorities not found",
				priorities.containsAll(expected));
	}
}
