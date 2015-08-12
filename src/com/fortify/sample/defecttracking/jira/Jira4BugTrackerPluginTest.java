/*
 * (C) Copyright 2015 Hewlett-Packard Development Company, L.P.
 */

package com.fortify.sample.defecttracking.jira;

import java.io.*;
import java.util.*;

import org.apache.commons.logging.*;
import org.junit.*;

import com.fortify.pub.bugtracker.support.*;



public class Jira4BugTrackerPluginTest {

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

	@Test
	public void testPluginFunctionality() throws Exception {


		/**
		 * Depends on a JIRA server setup with following projects
		 *
		 * BANK with 0 versions
		 * GOAT with 2 versions
		 */

		UserAuthenticationStore credentials = new UserAuthenticationStore() {

			public String getUserName() {
				return JIRA_USERNAME;
			}

			public String getPassword() {
				return JIRA_PASSWORD;
			}
		};

		Jira4BugTrackerPlugin plugin = new Jira4BugTrackerPlugin();

		List<BugTrackerConfig> config = plugin.getConfiguration();
		Assert.assertEquals(4, config.size());

		// assert defaults from properties file
		for (BugTrackerConfig c : config) {
			if (c.getIdentifier().equals("jiraUrl")) {
				Assert.assertEquals("http://localhost:8280", c.getValue());
			} else if (c.getIdentifier().equals("project")) {
				Assert.assertEquals("DEF_PROJECT", c.getValue());
			} else if (c.getIdentifier().equals("issueType")) {
				Assert.assertEquals("Task", c.getValue());
			} else if (c.getIdentifier().equals("(display-only)supportedVersions")) {
				//No matter
			} else {
				Assert.fail("invalid config item: " + c.getIdentifier());
			}
		}

		Map<String,String> values = new HashMap<String, String>();
		values.put("jiraUrl", "http://invalidurl");
		values.put("project", "invalid");
		values.put("issueType", "invalid");

		plugin.setConfiguration(values);
		try {
			plugin.testConfiguration(credentials);
			Assert.fail("Did not get exception for invalid configuration");
		} catch(BugTrackerException e) {
			// ok
		}

		values.put("jiraUrl", JIRA_URL);
		values.put("project", "GOAT");
		values.put("issueType", "Bug");

		plugin.setConfiguration(values);

		UserAuthenticationStore badCredentials = new UserAuthenticationStore() {

			public String getUserName() {
				return "user";
			}

			public String getPassword() {
				return "password";
			}
		};

		try {
			plugin.testConfiguration(badCredentials);
			Assert.fail("Did not get exception for invalid credentials");
		} catch(BugTrackerAuthenticationException e) {
			// ok
		}


		plugin.testConfiguration(credentials);

		Assert.assertTrue(plugin.getLongDisplayName().contains(JIRA_URL));

		IssueDetail issue = new IssueDetail();
		issue.setDescription("description");
		issue.setSummary("summary");
		issue.setCategory("category");
		issue.setAssignedUsername("assignedUsername");
		issue.setFileName("fileName");
		issue.setIssueDeepLink("issueDeepLink");
		issue.setIssueInstanceId("issueInstanceId");
		issue.setLineNumber(10);
		issue.setProjectName("projectName");
		issue.setProjectVersionName("projectVersionName");

		IssueComment c = new IssueComment();
		c.setBody("body1");
		c.setTimestamp(new Date());
		c.setUsername("username");
		issue.setComments(Arrays.asList(c));

		Map<String,String> customTags = new HashMap<String, String>();
		customTags.put("tag1", "value1");

		issue.setCustomTags(customTags);


		List<BugParam> params = plugin.getBugParameters(issue, credentials);

		Assert.assertEquals(8, params.size());

		BugParamChoice issueTypeParam = (BugParamChoice)findParam("issueType", params);
		BugParamChoice projectParam = (BugParamChoice)findParam("project", params);
		BugParamChoice priorityParam = (BugParamChoice)findParam("priority", params);
		BugParam assigneeParam = findParam("assignee", params);

		Assert.assertEquals(4, issueTypeParam.getChoiceList().size());
		Assert.assertEquals(5, priorityParam.getChoiceList().size());
		Assert.assertTrue(projectParam.getChoiceList().contains("BANK"));
		Assert.assertTrue(projectParam.getChoiceList().contains("GOAT"));

		Assert.assertEquals("assignedUsername",assigneeParam.getValue());
		Assert.assertEquals("GOAT",projectParam.getValue());
		Assert.assertEquals("Bug",issueTypeParam.getValue());

		projectParam.setValue(null);
		List<BugParam> params2 = plugin.onParameterChange(issue, "project", params, credentials);
		Assert.assertEquals(6, params2.size());

		projectParam.setValue("BANK");
		List<BugParam> params4 = plugin.onParameterChange(issue, "project", params, credentials);
		Assert.assertEquals(8, params4.size());
		BugParamChoice affectsVersionParam = (BugParamChoice)findParam("affectsVersion", params4);
		Assert.assertEquals(0, affectsVersionParam.getChoiceList().size());
		issueTypeParam = (BugParamChoice)findParam("issueType", params4);
		Assert.assertEquals(4, issueTypeParam.getChoiceList().size());
		Assert.assertNull(issueTypeParam.getValue());

		projectParam.setValue("GOAT");
		List<BugParam> params3 = plugin.onParameterChange(issue, "project", params, credentials);
		Assert.assertEquals(8, params3.size());
		affectsVersionParam = (BugParamChoice)findParam("affectsVersion", params3);
		Assert.assertEquals(2, affectsVersionParam.getChoiceList().size());
		Assert.assertNull(affectsVersionParam.getValue());
		issueTypeParam = (BugParamChoice)findParam("issueType", params4);
		Assert.assertEquals(4, issueTypeParam.getChoiceList().size());
		Assert.assertNull(issueTypeParam.getValue());

		Map<String,String> paramValues = new HashMap<String, String>();
		paramValues.put("description", findParam("description", params4).getValue());
		paramValues.put("summary", findParam("summary", params4).getValue());
		paramValues.put("project", "GOAT");
		paramValues.put("issueType", "Task");
		paramValues.put("priority", "Major");
		paramValues.put("dueIn", "7 days");
		paramValues.put("paramAssignee", null);

		BugSubmission bugSubmission = new BugSubmission();
		bugSubmission.setParams(paramValues);
		bugSubmission.setIssueDetail(issue);

		try {
			plugin.fileBug(bugSubmission, badCredentials);
			Assert.fail("Did not get exception for invalid credentials");
		} catch(BugTrackerAuthenticationException e) {
			// ok
		}

		Bug bug = plugin.fileBug(bugSubmission, credentials);
		Assert.assertNotNull(bug.getBugId());
		Assert.assertEquals("Open", bug.getBugStatus());


		System.out.println("Bug Logged: " + bug.getBugId());

		Bug bug2 = plugin.fetchBugDetails(bug.getBugId(), credentials);
		Assert.assertNotNull(bug2.getBugId());
		Assert.assertEquals("Open", bug2.getBugStatus());


	}

	public BugParam findParam(String paramIdentifier, List<BugParam> params) {
		for (final BugParam param : params) {
			if (paramIdentifier.equals(param.getIdentifier())) {
				return param;
			}
		}
		return null;
	}

}
