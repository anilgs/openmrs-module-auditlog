/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.auditlog.api;

import java.util.Date;
import java.util.List;

import org.openmrs.Concept;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;

/**
 * Contains service methods related to {@link AuditLog}s
 */
public interface AuditLogService extends OpenmrsService {
	
	/**
	 * Fetches the audit log entries matching the specified arguments
	 * 
	 * @param clazzes the class type to match against e.g for objects of type {@link Concept}
	 * @param actions the list of {@link Action}s to match against
	 * @param startDate the creation date of the log entries to return should be after or equal to
	 *            this date
	 * @param endDate the creation date of the log entries to return should be before or equal to
	 *            this date
	 * @param start index to start with (defaults to 0 if <code>null<code>)
	 * @param length number of results to return (default to return all matching results if
	 *            <code>null<code>)
	 * @return a list of matching {@link AuditLog}s
	 * @should return all audit logs in the database if all args are null
	 * @should match on the specified audit log actions
	 * @should match on the specified classes
	 * @should return logs created on or after the specified startDate
	 * @should return logs created on or before the specified endDate
	 * @should return logs created within the specified start and end dates
	 * @should reject a start date that is in the future
	 * @should ignore end date it it is in the future
	 * @should sort the logs by date of creation starting with the latest
	 * @should include logs for subclasses when getting logs by type
	 */
	public List<AuditLog> getAuditLogs(List<Class<? extends OpenmrsObject>> clazzes, List<Action> actions, Date startDate,
	                                   Date endDate, Integer start, Integer length);
	
	/**
	 * Fetches a saved object with the specified objectId
	 * 
	 * @param id the id to match against
	 * @return the matching saved object
	 * @should get the saved object matching the specified arguments
	 */
	public <T> T getObjectById(Class<T> clazz, Integer id);
	
	/**
	 * Fetches a saved object with the specified uuid
	 * 
	 * @param uuid the uuid to match against
	 * @return the matching saved object
	 * @should get the saved object matching the specified arguments
	 */
	public <T> T getObjectByUuid(Class<T> clazz, String uuid);
}
