/*
 * (C) Copyright 2015 Hewlett-Packard Development Company, L.P.
 */

package com.fortify.sample.defecttracking.jira;

import java.net.*;
import java.rmi.RemoteException;
import java.util.*;

import org.apache.commons.lang.*;
import org.apache.commons.logging.*;

import com.atlassian.jira.rpc.soap.client.*;
import com.fortify.pub.bugtracker.plugin.*;
import com.fortify.pub.bugtracker.support.*;

import static com.fortify.pub.bugtracker.support.BugTrackerPluginConstants.*;

/**
 * Bug tracking plugin for Atlassian JIRA 4.
 *
 */
@BugTrackerPluginImplementation
public class Jira4BugTrackerPlugin extends AbstractBatchBugTrackerPlugin {

	private static final Log LOG = LogFactory.getLog(Jira4BugTrackerPlugin.class);

	protected static final String JIRA_URL = "jiraUrl";
	protected static final String JIRA_ISSUE_TYPE = "issueType";
	protected static final String JIRA_PROJECT = "project";

	private static final String PARAM_AFFECTS_VERSION = "affectsVersion";
	private static final String PARAM_ASSIGNEE = "assignee";
	private static final String PARAM_DUE_IN = "dueIn";
	private static final String PARAM_PRIORITY = "priority";
	private static final String PARAM_SUMMARY = "summary";
	private static final String PARAM_DESCRIPTION = "description";

	private static final String STATUS_OPEN = "Open";
	private static final String STATUS_INPROGRESS = "In Progress";
	private static final String STATUS_REOPENED = "Reopened";
	private static final String STATUS_RESOLVED = "Resolved";
	private static final String STATUS_CLOSED = "Closed";
	private static final String STATUS_VERIFIED = "Verified";

	private static final String RESOLUTION_FIXED = "Fixed";
	private static final String RESOLUTION_WONT_FIX = "Won't Fix";
	private static final String RESOLUTION_DUPLICATE = "Duplicate";
	private static final String RESOLUTION_INCOMPLETE = "Incomplete";
	private static final String RESOLUTION_CANNOT_REPRODUCE = "Cannot Reproduce";

	private static final String ACTION_REOPEN = "Reopen Issue";

    private static final String SUPPORTED_VERSIONS = "6.x";

	private Map<String, String> configValues = new HashMap<String, String>();

	public Bug fetchBugDetails(String bugId, UserAuthenticationStore credentials) {
		Jira4PluginConnection connection = null;
		try {
			connection = getReusableConnection(credentials);
			final Bug bug = connection.fetchDetails(bugId);
			return bug;
		} catch (final RemoteException e) {
			LOG.info("JIRA Error fetchBugDetails",e);
			throw new BugTrackerException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} finally {
			if (connection != null) {
				connection.closeJiraConnection();
			}
		}
	}

	public Bug fileBug(BugSubmission bug, UserAuthenticationStore credentials) {
		return fileBug(bug.getParams(), credentials);
	}

	public String getBugDeepLink(String bugId) {
		final StringBuilder sb = new StringBuilder(configValues.get(JIRA_URL));
		if (sb.charAt(sb.length() - 1) != '/') {
			sb.append('/');
		}
		sb.append("browse/");
		sb.append(bugId);
		return sb.toString();
	}

	public List<BugParam> getBugParameters(IssueDetail issueDetail, UserAuthenticationStore credentials) {
		// JIRA 4.4 introduced a method called getFieldsForCreate in Aug 2011 but for compatibility throughout the 4.X suite, we cannot use it.
		final List<BugParam> initialFields = new ArrayList<BugParam>();
		Jira4PluginConnection connection = null;
		try {
			connection = getReusableConnection(credentials);

			BugParam summaryParam = new BugParamText()
					.setIdentifier(PARAM_SUMMARY)
					.setDisplayLabel("Bug Summary")
					.setRequired(true)
					.setDescription("Title of the bug to be logged");
			if (issueDetail == null) {
				summaryParam = summaryParam.setValue("Fix $ATTRIBUTE_CATEGORY$ in $ATTRIBUTE_FILE$");
			} else {
				summaryParam = summaryParam.setValue(issueDetail.getSummary());
			}
			initialFields.add(summaryParam);

			BugParam descriptionParam = new BugParamTextArea()
					.setIdentifier(PARAM_DESCRIPTION)
					.setDisplayLabel("Bug Description")
					.setRequired(true);
			if (issueDetail == null) {
				descriptionParam = descriptionParam.setValue("Issue Ids: $ATTRIBUTE_INSTANCE_ID$\n$ISSUE_DEEPLINK$");
			} else {
				descriptionParam.setValue(pluginHelper.buildDefaultBugDescription(issueDetail, true));
			}
			initialFields.add(descriptionParam);

			final BugParam project = new BugParamChoice()
					.setHasDependentParams(true)
					.setChoiceList(connection.getProjectKeys())
					.setDisplayLabel("Project Key")
					.setDescription("Project Key")
					.setIdentifier(JIRA_PROJECT)
					.setRequired(true)
					.setValue(configValues.get(JIRA_PROJECT));
			initialFields.add(project);

			final BugParam priority = new BugParamChoice()
					.setChoiceList(connection.getPriorityNames())
					.setDisplayLabel("Priority")
					.setIdentifier(PARAM_PRIORITY)
					.setRequired(true);
			initialFields.add(priority);

			final StringBuilder dueInDescription = new StringBuilder("Optional timeframe for a fix within development. Can be adjusted within ");
			dueInDescription.append(getShortDisplayName());
			dueInDescription.append(" after filing.");
			final BugParam dueIn = new BugParamChoice()
					.setChoiceList(Arrays.asList("7 days", "14 days", "90 days", "180 days"))
					.setDisplayLabel("Due In")
					.setDescription(dueInDescription.toString())
					.setIdentifier(PARAM_DUE_IN);
			initialFields.add(dueIn);

			BugParam assignee = new BugParamText()
				.setDisplayLabel("Assignee")
				.setIdentifier(PARAM_ASSIGNEE)
				.setRequired(false);
			if (issueDetail != null) {
				assignee = assignee.setValue(issueDetail.getAssignedUsername());
			}
			initialFields.add(assignee);

			if (configValues.get(JIRA_PROJECT) != null) {

				final List<String> issueTypes = connection.getIssueTypes(project.getValue());
				String defaultIssueType = configValues.get(JIRA_ISSUE_TYPE);

				final BugParam issueType = new BugParamChoice()
					.setChoiceList(issueTypes)
					.setDisplayLabel("Issue Type")
					.setIdentifier(JIRA_ISSUE_TYPE)
					.setRequired(true);
				if (issueTypes.contains(defaultIssueType)) {
					issueType.setValue(defaultIssueType);
				}
				initialFields.add(issueType);

				final List<String> versions = connection.getVersions(project.getValue());
				final BugParam affectsVersion = new BugParamChoice()
					.setChoiceList(versions)
					.setDisplayLabel("Affects version")
					.setIdentifier(PARAM_AFFECTS_VERSION);
				initialFields.add(affectsVersion);
			}


		} catch (final RemoteException e) {
			LOG.info("JIRA Error getBugParameters",e);
			throw new BugTrackerException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} finally {
			if (connection != null) {
				connection.closeJiraConnection();
			}
		}

		return initialFields;
	}

	public List<BugTrackerConfig> getConfiguration() {

        final BugTrackerConfig supportedVersions = new BugTrackerConfig()
                .setIdentifier(DISPLAY_ONLY_SUPPORTED_VERSION)
                .setDisplayLabel("Supported Versions")
                .setDescription("Bug Tracker versions supported by the plugin")
                .setValue(SUPPORTED_VERSIONS)
                .setRequired(false);

		final BugTrackerConfig jiraHost = new BugTrackerConfig()
				.setIdentifier(JIRA_URL)
				.setDisplayLabel("JIRA URL")
				.setDescription("Base jira url, such as http://jira")
				.setRequired(true);

		final BugTrackerConfig project = new BugTrackerConfig()
				.setIdentifier(JIRA_PROJECT)
				.setDisplayLabel("Default Project Key")
				.setDescription("Default project for filing bugs, e.g. PROJ")
				.setRequired(true);

		final BugTrackerConfig issueType = new BugTrackerConfig()
				.setIdentifier(JIRA_ISSUE_TYPE)
				.setDisplayLabel("Default Issue Type")
				.setDescription("Type of issue to file, e.g. Bug, Task, or other known value.")
				.setValue("Bug")
				.setRequired(true);

		final List<BugTrackerConfig> configs = Arrays.asList(supportedVersions, jiraHost, project, issueType);

		pluginHelper.populateWithDefaultsIfAvailable(configs);
		return configs;
	}

	public String getLongDisplayName() {
		final StringBuilder sb = new StringBuilder(getShortDisplayName());
		sb.append(" (");
		sb.append(configValues.get(JIRA_URL));
		sb.append(')');
		return sb.toString();
	}

	public String getShortDisplayName() {
		return "JIRA";
	}

	public List<BugParam> onParameterChange(IssueDetail issueDetail, String changedParamIdentifier, List<BugParam> currentValues, UserAuthenticationStore credentials) {

		Jira4PluginConnection connection=null;
		if (JIRA_PROJECT.equals(changedParamIdentifier)) {
			try {
				connection = getReusableConnection(credentials);
				final BugParam project = pluginHelper.findParam(JIRA_PROJECT, currentValues);

				if (!StringUtils.isEmpty(project.getValue())) {

					final List<String> issueTypes = connection.getIssueTypes(project.getValue());
					final BugParam issueType = new BugParamChoice()
						.setChoiceList(issueTypes)
						.setDisplayLabel("Issue Type")
						.setIdentifier(JIRA_ISSUE_TYPE)
						.setRequired(true);
					if (issueTypes.contains(configValues.get(JIRA_ISSUE_TYPE))) {
						issueType.setValue(configValues.get(JIRA_ISSUE_TYPE));
					}
					addOrReplaceParam(issueType, currentValues);

					final List<String> versions = connection.getVersions(project.getValue());
					final BugParam affectsVersion = new BugParamChoice()
							.setChoiceList(versions)
							.setDisplayLabel("Affects version")
							.setIdentifier(PARAM_AFFECTS_VERSION);
					addOrReplaceParam(affectsVersion, currentValues);
					return currentValues;

				}

				/* remove affectsVersion and issueType params */
				pluginHelper.removeParam(PARAM_AFFECTS_VERSION, currentValues);
				pluginHelper.removeParam(JIRA_ISSUE_TYPE, currentValues);
				return currentValues;

			} catch (final RemoteException e) {
				LOG.info("JIRA Error onParameterChange",e);
				throw new BugTrackerException(Jira4PluginConnection.findHelpfulMessage(e), e);
			} finally {
				if (connection != null) {
					connection.closeJiraConnection();
				}
			}
		}
		return null;

	}

	public boolean requiresAuthentication() {
		return true;
	}

	public void setConfiguration(Map<String, String> configuration) {
		configValues = configuration;

		String url = configuration.get(JIRA_URL);
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			throw new BugTrackerException("JIRA URL protocol should be either http or https");
		}
		
		if (url.endsWith("/")) {
			url = url.substring(0,url.length()-1);
			configuration.put(JIRA_URL, url);
		}

		try {
			URL urltrue = new URL(configValues.get(JIRA_URL));
			urltrue.toURI();
			if (urltrue.getHost().length() == 0) {
				throw new BugTrackerException("JIRA host cannot be empty");
			}
		} catch (MalformedURLException e)
		{
			throw new BugTrackerException("Invalid JIRA URL: " + configValues.get(JIRA_URL));
		}
		catch (URISyntaxException e) {
			throw new BugTrackerException("Invalid JIRA URL: " + configValues.get(JIRA_URL));
		}

	}

	public void testConfiguration(UserAuthenticationStore credentials) {
		Jira4PluginConnection connection=null;
		try {
			final List<String> errorMessages = new ArrayList<String>(1);
			//Explicitly make a new set of credentials for a new person
			connection = new Jira4PluginConnection(credentials.getUserName(), credentials.getPassword(), configValues.get(JIRA_URL));

			final String projectKey = configValues.get(JIRA_PROJECT);
			final List<String> projects = connection.getProjectKeys();
			if (!projects.contains(projectKey)) {
				errorMessages.add("No project named " + projectKey + " was found with your permissions. Test with a different username or use one of the following projects: "
						+ StringUtils.join(projects, ", ") + '.');
			}

			String issueType = configValues.get(JIRA_ISSUE_TYPE);
			final List<String> issueTypes = connection.getIssueTypes(projectKey);
			
			for (String validType :issueTypes) {
				if (validType.equalsIgnoreCase(issueType))
				{
					issueType = validType;
					configValues.put(JIRA_ISSUE_TYPE, validType);
				}
			}
			
			if (!issueTypes.contains(issueType)) {
				errorMessages.add("No issue type " + issueType + " was found for project " + projectKey + ". Please try one of: " + StringUtils.join(issueTypes, ", ") + '.');
			}

			if (!errorMessages.isEmpty()) {
				throw new BugTrackerException(StringUtils.join(errorMessages, '\n'));
			}
		} catch (RemoteAuthenticationException e) {
			LOG.info("JIRA Error testConfiguration",e);
			throw new BugTrackerAuthenticationException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} catch (final RemoteException e) {
			throw new BugTrackerException("Error occured during test: " + Jira4PluginConnection.findHelpfulMessage(e), e);
		} finally {
			if (connection != null) {
				connection.closeJiraConnection();
			}
		}
	}

	@Override
	public String toString() {
		return getLongDisplayName();
	}

	public void validateCredentials(UserAuthenticationStore credentials) {

		Jira4PluginConnection connection = null;
		try {
			//Explicitly make a new set of credentials for a new person
			connection = new Jira4PluginConnection(credentials.getUserName(), credentials.getPassword(), configValues.get(JIRA_URL));
		} catch (RemoteAuthenticationException e) {
			throw new BugTrackerAuthenticationException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} catch (final RemoteException e) {
			throw new BugTrackerException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} finally {
			if (connection != null) {
				connection.closeJiraConnection();
			}
		}
	}

	private void addOrReplaceParam(BugParam param, List<BugParam> inHere) {
		boolean alreadyPresent = false;
		for (int i=0; i<inHere.size(); i++) {
			//Maybe we need to replace a parameter
			if (StringUtils.equals(inHere.get(i).getIdentifier(), param.getIdentifier())) {
				alreadyPresent = true;
				inHere.set(i, param);
				break;
			}
		}
		if (!alreadyPresent) {
			//We did not find the parameter, so add it ad the end
			inHere.add(param);
		}
	}



	private Jira4PluginConnection getReusableConnection(UserAuthenticationStore credentials) throws RemoteException {
		try {
			return new Jira4PluginConnection(credentials.getUserName(), credentials.getPassword(), configValues.get(JIRA_URL));
		} catch (RemoteAuthenticationException e) {
			LOG.info("JIRA Error getConnection",e);
			throw new BugTrackerAuthenticationException(Jira4PluginConnection.findHelpfulMessage(e), e);
		}
	}

	private String trimStringFieldValue(String val) {
		if (val.length() > 255) {
			return val.substring(0, 252) + "...";
		}
		return val;
	}
	
	private Bug fileBug(Map<String, String> params, UserAuthenticationStore credentials) {
		Bug retval = null;
		Jira4PluginConnection connection = null;
		try {
			connection = getReusableConnection(credentials);
			Calendar dueDate = null;
			if (!StringUtils.isEmpty(params.get(PARAM_DUE_IN))) {
				try {
					dueDate = Calendar.getInstance();
					final Integer days = Integer.valueOf(params.get(PARAM_DUE_IN).replaceAll("\\D", ""));
					dueDate.add(Calendar.DAY_OF_MONTH, days);
				} catch (final NumberFormatException e) {
					LOG.info("Unable to set bug due date", e);
				}
			}
			retval = connection.createNewIssue(params.get(JIRA_PROJECT),
					trimStringFieldValue(params.get(PARAM_SUMMARY)),
					params.get(PARAM_DESCRIPTION),
					dueDate,
					params.get(PARAM_PRIORITY),
					params.get(JIRA_ISSUE_TYPE),
					params.get(PARAM_ASSIGNEE),
					params.get(PARAM_AFFECTS_VERSION));
		} catch (final RemoteException e) {
			LOG.info("JIRA Error fileBug",e);
			String errorMessage = Jira4PluginConnection.findHelpfulMessage(e);
			
			errorMessage = errorMessage.replaceFirst("^([^\\w]|[\\s])*", "");
			errorMessage = errorMessage.replaceFirst("([^\\w]|[\\s])*$", "");
			
			if (errorMessage.length() == 0) {
				errorMessage = "Unknown error while trying to file a bug.";
			}
			
			throw new BugTrackerException(errorMessage, e);
		} finally {
			if (connection != null) {
				connection.closeJiraConnection();
			}
		}
		return retval;
	}

	public List<BugParam> getBatchBugParameters(UserAuthenticationStore credentials) {
		return getBugParameters(null, credentials);
	}
	public List<BugParam> onBatchBugParameterChange(String changedParamIdentifier, List<BugParam> currentValues, UserAuthenticationStore credentials) {
		return onParameterChange(null, changedParamIdentifier, currentValues, credentials);
	}
	public Bug fileMultiIssueBug(MultiIssueBugSubmission bug, UserAuthenticationStore credentials) {
		return fileBug(bug.getParams(), credentials);
	}
	public boolean isBugOpen(Bug bug, UserAuthenticationStore credentials) {
		return STATUS_OPEN.equals(bug.getBugStatus()) || STATUS_INPROGRESS.equals(bug.getBugStatus()) || STATUS_REOPENED.equals(bug.getBugStatus());
	}
	public boolean isBugClosed(Bug bug, UserAuthenticationStore credentials) {
		return STATUS_RESOLVED.equals(bug.getBugStatus()) || STATUS_CLOSED.equals(bug.getBugStatus()) || STATUS_VERIFIED.equals(bug.getBugStatus());
	}
	public boolean isBugClosedAndCanReOpen(Bug bug, UserAuthenticationStore credentials) {
		return isBugClosed(bug, credentials) && (RESOLUTION_FIXED.equals(bug.getBugResolution()) || RESOLUTION_INCOMPLETE.equals(bug.getBugResolution()));
	}
	public void reOpenBug(Bug bug, String comment, UserAuthenticationStore credentials) {
		Jira4PluginConnection connection = null;
		try {
			connection = getReusableConnection(credentials);
			connection.progressWorkflow(bug.getBugId(), ACTION_REOPEN);
			connection.addComment(bug.getBugId(), comment);
		} catch (final RemoteException e) {
			LOG.info("JIRA Error reOpenBug",e);
			throw new BugTrackerException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} finally {
			if (connection != null ) {
				connection.closeJiraConnection();
			}
		}
	}
	public void addCommentToBug(Bug bug, String comment, UserAuthenticationStore credentials) {
		Jira4PluginConnection connection = null;
		try {
			connection = getReusableConnection(credentials);
			connection.addComment(bug.getBugId(), comment);
		} catch (final RemoteException e) {
			LOG.info("JIRA Error addComment",e);
			throw new BugTrackerException(Jira4PluginConnection.findHelpfulMessage(e), e);
		} finally {
			if (connection != null ) {
				connection.closeJiraConnection();
			}
		}
	}
}
