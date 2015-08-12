/*
 * (C) Copyright 2015 Hewlett-Packard Development Company, L.P.
 */

package com.fortify.sample.defecttracking.jira;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.axis.AxisFault;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.atlassian.jira.rpc.soap.client.*;
import com.atlassian.jira_soapclient.SOAPSession;
import com.fortify.pub.bugtracker.support.Bug;

/**
 * Utility for working with JIRA's APIs.
 * Instructions from {@link http://confluence.atlassian.com/display/JIRA041/Creating+a+SOAP+Client}
 * Using Atlassian library from {@link https://svn.atlassian.com/svn/public/atlassian/rpc-jira-plugin/tags/atlassian_jira_4_1_1_1/jira-soapclient/}
 * Relevant Javadoc {@link http://docs.atlassian.com/software/jira/docs/api/rpc-jira-plugin/4.1-1/}
 *
 * @author costlowe
 *
 */
public class Jira4PluginConnection {

	private static final Log LOG = LogFactory.getLog(Jira4PluginConnection.class);
	private final String _authToken;

	private final JiraSoapService _jiraSoapService;

	/**
	 * Opens a remote connection to JIRA and encapsulate its calls.
	 *
	 * @param userName
	 * @param password
	 * @param jiraBaseUrl
	 * @throws RemoteException
	 */
	public Jira4PluginConnection(String userName, String password, String jiraBaseUrl) throws RemoteException {
		final String jiraWebServiceURL = jiraBaseUrl + "/rpc/soap/jirasoapservice-v2";
		SOAPSession soapSession;
		try {
			soapSession = new SOAPSession(new URL(jiraWebServiceURL));
		} catch (final MalformedURLException e) {
			throw new RemoteException("Invalid JIRA URL", e);
		}
		soapSession.connect(userName, password);
		_jiraSoapService = soapSession.getJiraSoapService();
		_authToken = soapSession.getAuthenticationToken();
	}

	/**
	 * Close the remote connection and tell JIRA that our temporary key is no longer valid.
	 * Your object instance will (intentionally) be useless after calling this.
	 *
	 * @throws RemoteException
	 */
	public void closeJiraConnection() {
		try {
			_jiraSoapService.logout(_authToken);
		} catch (final RemoteException e) {
			LOG.trace("Unable to close jira connection, probably already closed", e);
		}
	}

	/**
	 *
	 * @param projectKey Which project to file the issue in.
	 * @param summary Summary/title of the issue.
	 * @param description Description of the issue. Should include a deep-link into Software Security Center.
	 * @param dueDate Nullable date of expected fix.
	 * @param priorityName Priority name as expected by JIRA, e.g. Critical.
	 * @param issueTypeName Type of issue to file, e.g. Bug.
	 * @param assignee Nullable name of issue assignee. If null, assignment will be done in JIRA.
	 * @param affectsVersion Nullable version of the project.
	 * @return The issue that was created inside JIRA
	 * @throws RemoteException
	 *             if the issue cannot be created
	 */
	public Bug createNewIssue(String projectKey, String summary, String description, Calendar dueDate, String priorityName, String issueTypeName, String assignee, String affectsVersion)
			throws RemoteException {
		final String priorityId = findIdFromName(priorityName, _jiraSoapService.getPriorities(_authToken));
		final String issueTypeId = findIdFromName(issueTypeName, _jiraSoapService.getIssueTypes(_authToken));
		final RemoteVersion[] affectsVersions = parseAffectsVersion(affectsVersion, projectKey);

		RemoteIssue remoteIssue = new RemoteIssue();
		remoteIssue.setAffectsVersions(affectsVersions);
		remoteIssue.setAssignee(StringUtils.isEmpty(assignee) ? null : assignee);
		remoteIssue.setDescription(description);
		remoteIssue.setDuedate(dueDate);
		remoteIssue.setPriority(priorityId);
		remoteIssue.setProject(projectKey);
		remoteIssue.setSummary(summary);
		remoteIssue.setType(issueTypeId);

		remoteIssue = _jiraSoapService.createIssue(_authToken, remoteIssue);

		final Bug retval = fetchDetails(remoteIssue.getKey());

		return retval;
	}

	public void addComment(String issueId, String comment) throws RemoteException {
		RemoteComment rc = new RemoteComment();
		rc.setBody(comment);
		_jiraSoapService.addComment(_authToken, issueId, rc);
	}

	public void progressWorkflow(String issueId, String action) throws RemoteException {
		final RemoteNamedObject[] actions = _jiraSoapService.getAvailableActions(_authToken, issueId);
		String statusId = null;
		for (final RemoteNamedObject raction : actions) {
			if (StringUtils.equals(raction.getName(), action)) {
				statusId = raction.getId();
				break;
			}
		}
		if (statusId != null) {
			_jiraSoapService.progressWorkflowAction(_authToken, issueId, statusId, null);
		}
	}

	/**
	 *
	 * @param issueId The issue id within JIRA
	 * @return A representation of the Bug's current status
	 */
	public Bug fetchDetails(String issueId) {
		Bug retval = new Bug(issueId, "UNKNOWN");
		RemoteIssue issue;
		try {
			issue = _jiraSoapService.getIssue(_authToken, issueId);
			final RemoteStatus[] statuses = _jiraSoapService.getStatuses(_authToken);
			for (final RemoteStatus status : statuses) {
				if (StringUtils.equals(status.getId(), issue.getStatus())) {
					retval.setBugStatus(status.getName());
					break;
				}
			}
			final RemoteResolution[] resolutions = _jiraSoapService.getResolutions(_authToken);
			for (final RemoteResolution resolution : resolutions) {
				if (StringUtils.equals(resolution.getId(), issue.getResolution())) {
					retval.setBugResolution(resolution.getName());
					break;
				}
			}
		} catch (final RemoteException e) {
			LOG.info("Unable to get remote status", e);
			retval = null;
		}
		return retval;
	}

	/**
	 *
	 * @return The types of issues that can be filed.
	 * @throws RemoteException
	 */
	public List<String> getIssueTypes(String projectKey) throws RemoteException {
		RemoteProject project = _jiraSoapService.getProjectByKey(_authToken, projectKey);
		return toTextList(_jiraSoapService.getIssueTypesForProject(_authToken, project.getId()));
	}

	/**
	 *
	 * @return Names of the priorities that can be applied to the issue.
	 * @throws RemoteException
	 */
	public List<String> getPriorityNames() throws RemoteException {
		return toTextList(_jiraSoapService.getPriorities(_authToken));

	}

	/**
	 *
	 * @return List of projects that we are permissioned for.
	 * @throws RemoteException
	 */
	public List<String> getProjectKeys() throws RemoteException {
		final RemoteProject[] projects = _jiraSoapService.getProjectsNoSchemes(_authToken);
		final List<String> projectKeys = new ArrayList<String>(projects.length);
		for (final RemoteProject project : projects) {
			projectKeys.add(project.getKey());
		}
		return projectKeys;
	}

	/**
	 *
	 * @param projectKey Which project to look at
	 * @return List of available versions for the project.
	 * @throws RemoteException
	 */
	public List<String> getVersions(String projectKey) throws RemoteException {
		return toTextList(_jiraSoapService.getVersions(_authToken, projectKey));
	}

	private String findIdFromName(String findName, AbstractNamedRemoteEntity[] fromCollection) {
		String retval = null;
		for (final AbstractNamedRemoteEntity entity : fromCollection) {
			if (StringUtils.equals(entity.getName(), findName)) {
				retval = entity.getId();
				break;
			}
		}
		return retval;
	}

	private String findNameFromId(String id, AbstractNamedRemoteEntity[] collection) {
		String retval = null;
		for (final AbstractNamedRemoteEntity entity : collection) {
			if (StringUtils.equals(entity.getId(), id)) {
				retval = entity.getName();
				break;
			}
		}
		return retval;
	}

	private RemoteVersion[] parseAffectsVersion(String affectsVersion, String project) throws RemoteException {
		if (StringUtils.isEmpty(affectsVersion)) {
			return null;
		}

		final RemoteVersion[] retval;

		final RemoteVersion[] knownVersions = _jiraSoapService.getVersions(_authToken, project);
		final List<RemoteVersion> versions = new ArrayList<RemoteVersion>(knownVersions.length);

		for (final RemoteVersion version : knownVersions) {
			if (StringUtils.equals(version.getName(), affectsVersion)) {
				versions.add(version);
			}
		}
		retval = versions.toArray(new RemoteVersion[0]);

		return retval;
	}

	private List<String> toTextList(AbstractNamedRemoteEntity[] collection) {
		final List<String> retval = new ArrayList<String>(collection.length);
		for (final AbstractNamedRemoteEntity entity : collection) {
			retval.add(entity.getName());
		}
		return retval;
	}

	@Override
	protected void finalize() {
		// A polite way of cleaning up after ourselves within JIRA.
		if (_jiraSoapService != null) {
			closeJiraConnection();
		}
		try {
			super.finalize();
		} catch (final Throwable e) {
			LOG.trace("Issue cleaning up in JIRA 4", e);
		}
	}

	static String findHelpfulMessage(RemoteException e) {
		//JIRA doesn't put the useful information inside getMessage, so parse their format
		final String retval;
		if (e instanceof com.atlassian.jira.rpc.soap.client.RemoteException) {
			final String faultCode = ((com.atlassian.jira.rpc.soap.client.RemoteException) e).getFaultString();
			retval = (faultCode.indexOf(':') != -1) ? faultCode.substring(faultCode.indexOf(':') + 1) : faultCode;
		} else if (e instanceof AxisFault) {
			if (e.detail instanceof ConnectException) {
				retval = "Could not connect to JIRA server. Check the plugin configuration and ensure that the server is not down or overloaded.";
			} else {
				if (e.detail != null) {
					retval = "There is a problem during connection with JIRA server: " + e.detail.getMessage();
				} else {
					retval = "There is a problem during connection with JIRA server: " + e.getMessage();
				}
			}
		} else {
			retval = e.getMessage();
		}
		return retval;
	}

}
