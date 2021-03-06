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
package org.openmrs.module.auditlog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptDescription;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.OpenmrsObject;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.auditlog.AuditLog.Action;
import org.openmrs.module.auditlog.api.AuditLogService;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.openmrs.module.auditlog.util.AuditLogUtil;
import org.openmrs.test.BaseModuleContextSensitiveTest;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.test.annotation.NotTransactional;

/**
 * Contains tests for testing the core functionality of the module
 */
@SuppressWarnings("deprecation")
public class AuditLogBehaviorTest extends BaseModuleContextSensitiveTest {
	
	private static final String MODULE_TEST_DATA = "moduleTestData.xml";
	
	private ConceptService conceptService;
	
	private AuditLogService auditLogService;
	
	private EncounterService encounterService;
	
	@Before
	public void before() throws Exception {
		executeDataSet(MODULE_TEST_DATA);
		if (MonitoringStrategy.NONE_EXCEPT != AuditLogUtil.getMonitoringStrategy()) {
			AdministrationService as = Context.getAdministrationService();
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(
			    AuditLogConstants.GP_MONITORING_STRATEGY);
			gp.setPropertyValue(MonitoringStrategy.NONE_EXCEPT.name());
			as.saveGlobalProperty(gp);
		}
		Set<Class<?>> monitoredClasses = AuditLogUtil.getMonitoredClasses();
		if (monitoredClasses.size() != 3 || !monitoredClasses.contains(Concept.class)
		        || !monitoredClasses.contains(EncounterType.class)
		        || !monitoredClasses.contains(PatientIdentifierType.class)) {
			AdministrationService as = Context.getAdministrationService();
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(
			    AuditLogConstants.GP_MONITORED_CLASSES);
			gp.setPropertyValue(Concept.class.getName() + ", " + EncounterType.class.getName() + ", "
			        + PatientIdentifierType.class.getName());
			as.saveGlobalProperty(gp);
		}
		if (AuditLogUtil.getUnMonitoredClasses().size() != 1
		        || !AuditLogUtil.getUnMonitoredClasses().contains(EncounterType.class)) {
			AdministrationService as = Context.getAdministrationService();
			GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(
			    AuditLogConstants.GP_UN_MONITORED_CLASSES);
			gp.setPropertyValue(EncounterType.class.getName());
			as.saveGlobalProperty(gp);
		}
		
		conceptService = Context.getConceptService();
		encounterService = Context.getEncounterService();
		auditLogService = Context.getService(AuditLogService.class);
		
		//No log entries should be existing
		Assert.assertTrue(getAllLogs().isEmpty());
	}
	
	/**
	 * Utility method to get all logs
	 * 
	 * @return a list of {@link AuditLog}s
	 */
	private List<AuditLog> getAllLogs() {
		return auditLogService.getAuditLogs(null, null, null, null, null, null);
	}
	
	@Test
	@NotTransactional
	public void shouldCreateAnAuditLogEntryWhenANewObjectIsCreated() {
		Concept concept = new Concept();
		ConceptName cn = new ConceptName("new", Locale.ENGLISH);
		cn.setConcept(concept);
		concept.addName(cn);
		concept.setDatatype(conceptService.getConceptDatatype(4));
		concept.setConceptClass(conceptService.getConceptClass(4));
		conceptService.saveConcept(concept);
		List<AuditLog> logs = getAllLogs();
		Assert.assertNotNull(concept.getConceptId());
		//Should have created an entry for the concept and concept name
		Assert.assertEquals(2, logs.size());
		//The latest logs come first
		Assert.assertEquals(Action.CREATED, logs.get(0).getAction());
		Assert.assertEquals(Action.CREATED, logs.get(1).getAction());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateAnAuditLogEntryWhenAnObjectIsDeleted() throws Exception {
		EncounterType encounterType = encounterService.getEncounterType(6);
		encounterService.purgeEncounterType(encounterType);
		List<AuditLog> logs = getAllLogs();
		//Should have created a log entry for deleted Encounter type
		Assert.assertEquals(1, logs.size());
		Assert.assertEquals(Action.DELETED, logs.get(0).getAction());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateAnAuditLogEntryWhenAnObjectIsEdited() throws Exception {
		Concept concept = conceptService.getConcept(3);
		String oldConceptClassUuid = concept.getConceptClass().getUuid();
		String oldDatatypeUuid = concept.getDatatype().getUuid();
		ConceptClass cc = conceptService.getConceptClass(2);
		ConceptDatatype dt = conceptService.getConceptDatatype(3);
		String oldVersion = concept.getVersion();
		String newVersion = "1.11";
		Assert.assertFalse(cc.equals(concept.getConceptClass()));
		Assert.assertFalse(dt.equals(concept.getDatatype()));
		Assert.assertFalse(newVersion.equalsIgnoreCase(oldVersion));
		
		concept.setConceptClass(cc);
		concept.setDatatype(dt);
		concept.setVersion(newVersion);
		conceptService.saveConcept(concept);
		
		List<AuditLog> logs = getAllLogs();
		//Should have created a log entry for edited concept
		Assert.assertEquals(1, logs.size());
		AuditLog auditLog = logs.get(0);
		
		//Should have created entries for the changes properties and their old values
		Assert.assertEquals(Action.UPDATED, auditLog.getAction());
		//Check that there 3 property tag entries
		Map<String, String[]> changes = auditLog.getChanges();
		Assert.assertEquals(3, changes.size());
		Assert.assertEquals(AuditLogConstants.UUID_LABEL + oldConceptClassUuid, changes.get("conceptClass")[1]);
		Assert.assertEquals(AuditLogConstants.UUID_LABEL + oldDatatypeUuid, changes.get("datatype")[1]);
		Assert.assertEquals(oldVersion, changes.get("version")[1]);
		
		Assert.assertEquals(AuditLogConstants.UUID_LABEL + cc.getUuid(), changes.get("conceptClass")[0]);
		Assert.assertEquals(AuditLogConstants.UUID_LABEL + dt.getUuid(), changes.get("datatype")[0]);
		Assert.assertEquals(newVersion, changes.get("version")[0]);
	}
	
	@Test
	@NotTransactional
	public void shouldCreateNoLogEntryIfNoChangesAreMadeToAnExistingObject() throws Exception {
		EncounterType encounterType = encounterService.getEncounterType(2);
		encounterService.saveEncounterType(encounterType);
		Assert.assertTrue(getAllLogs().isEmpty());
	}
	
	@Test
	@NotTransactional
	public void shouldIgnoreDateChangedAndCreatedFields() throws Exception {
		Concept concept = conceptService.getConcept(3);
		//sanity checks
		Assert.assertNull(concept.getDateChanged());
		Assert.assertNull(concept.getChangedBy());
		concept.setDateChanged(new Date());
		concept.setChangedBy(Context.getAuthenticatedUser());
		conceptService.saveConcept(concept);
		Assert.assertTrue(getAllLogs().isEmpty());
	}
	
	@Test
	@NotTransactional
	public void shouldHandleInsertsOrUpdatesOrDeletesInEachTransactionIndependently() throws InterruptedException {
		final int N = 50;
		final Set<Thread> threads = new LinkedHashSet<Thread>();
		
		for (int i = 0; i < N; i++) {
			threads.add(new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						Context.openSession();
						Context.authenticate("admin", "test");
						Integer index = new Integer(Thread.currentThread().getName());
						EncounterService es = Context.getEncounterService();
						if (index == 0) {
							//Let's have a delete
							EncounterType existingEncounterType = es.getEncounterType(6);
							Assert.assertNotNull(existingEncounterType);
							es.purgeEncounterType(existingEncounterType);
						} else {
							EncounterType encounterType = null;
							if (index % 2 == 0) {
								//And some updates
								encounterType = es.getEncounterType(2);
								encounterType.setDescription("New Description-" + index);
							} else {
								//And some new rows inserted
								encounterType = new EncounterType("Encounter Type-" + index, "Description-" + index);
							}
							es.saveEncounterType(encounterType);
						}
					}
					finally {
						Context.closeSession();
					}
				}
			}, new Integer(i).toString()));
		}
		
		for (Thread thread : threads) {
			thread.start();
		}
		
		for (Thread thread : threads) {
			thread.join();
		}
		
		Assert.assertEquals(N, getAllLogs().size());
		
		List<Action> actions = new ArrayList<Action>();
		actions.add(Action.CREATED);//should match expected count of created log entries
		Assert.assertEquals(25, auditLogService.getAuditLogs(null, actions, null, null, null, null).size());
		
		actions.clear();
		actions.add(Action.UPDATED);//should match expected count of updated log entries
		Assert.assertEquals(24, auditLogService.getAuditLogs(null, actions, null, null, null, null).size());
		
		actions.clear();
		actions.add(Action.DELETED);//should match expected count of deleted log entries
		Assert.assertEquals(1, auditLogService.getAuditLogs(null, actions, null, null, null, null).size());
	}
	
	@Test
	@NotTransactional
	public void shouldNotCreateAuditLogsForUnMonitoredObjects() {
		Assert.assertFalse(OpenmrsUtil.collectionContains(AuditLogUtil.getMonitoredClasses(), Location.class.getName()));
		Location location = new Location();
		location.setName("najja");
		location.setAddress1("test address");
		Location savedLocation = Context.getLocationService().saveLocation(location);
		Assert.assertNotNull(savedLocation.getLocationId());//sanity check that it was actually created
		//Should not have created any logs
		Assert.assertTrue(getAllLogs().isEmpty());
	}
	
	@Test
	@NotTransactional
	public void shouldIgnoreChangesForStringFieldsFromNullToBlank() throws Exception {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType idType = ps.getPatientIdentifierType(1);
		idType.setFormat(null);
		ps.savePatientIdentifierType(idType);
		
		int originalLogCount = getAllLogs().size();
		idType.setFormat("");
		ps.savePatientIdentifierType(idType);
		Assert.assertEquals(originalLogCount, getAllLogs().size());
	}
	
	@Test
	@NotTransactional
	public void shouldIgnoreChangesForStringFieldsFromBlankToNull() throws Exception {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType idType = ps.getPatientIdentifierType(1);
		idType.setFormat("");
		idType = ps.savePatientIdentifierType(idType);
		//this will fail when required version is 1.9 since it converts blanks to null
		Assert.assertEquals("", idType.getFormat());
		
		int originalLogCount = getAllLogs().size();
		idType.setFormat(null);
		ps.savePatientIdentifierType(idType);
		Assert.assertEquals(originalLogCount, getAllLogs().size());
	}
	
	@Test
	@NotTransactional
	public void shouldBeCaseInsensitiveForChangesInStringFields() throws Exception {
		PatientService ps = Context.getPatientService();
		PatientIdentifierType idType = ps.getPatientIdentifierType(1);
		idType.setFormat("test");
		idType = ps.savePatientIdentifierType(idType);
		
		int originalLogCount = getAllLogs().size();
		idType.setFormat("TEST");
		ps.savePatientIdentifierType(idType);
		Assert.assertEquals(originalLogCount, getAllLogs().size());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateAnAuditLogEntryWhenAnElementIsRemovedFormAChildCollection() throws Exception {
		Concept concept = conceptService.getConcept(5089);
		//something with ConceptMaps having a blank uuids and now getting set, this should have been
		//fixed in later versions
		conceptService.saveConcept(concept);
		List<AuditLog> existingUpdateLogs = auditLogService.getAuditLogs(null, Collections.singletonList(Action.UPDATED),
		    null, null, null, null);
		//for the two concept maps and the concept
		int originalCount = concept.getDescriptions().size();
		Assert.assertTrue(originalCount > 0);
		concept.removeDescription(concept.getDescription());
		conceptService.saveConcept(concept);
		Assert.assertEquals(originalCount - 1, concept.getDescriptions().size());
		List<Class<? extends OpenmrsObject>> clazzes = new ArrayList<Class<? extends OpenmrsObject>>();
		clazzes.add(Concept.class);
		List<AuditLog> conceptLogs = auditLogService.getAuditLogs(null, Collections.singletonList(Action.UPDATED), null,
		    null, null, null);
		//TODO this test should check that the collection was updated
		Assert.assertEquals(existingUpdateLogs.size() + 1, conceptLogs.size());
		conceptLogs.removeAll(existingUpdateLogs);
		Assert.assertEquals(1, conceptLogs.size());
		Assert.assertEquals(conceptLogs.get(0).getObjectUuid(), concept.getUuid());
		
		//Hibernate doens't call interceptor.onDelete for an element that is removed from a child collection
		//List<AuditLog> descriptionLogs = auditLogService.getAuditLogs(null, Collections.singletonList(Action.DELETED), null, null, null, null);
		//Assert.assertEquals(1, descriptionLogs.size());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateAnAuditLogEntryWhenAnElementIsAddedToAChildCollection() throws Exception {
		Concept concept = conceptService.getConcept(5089);
		//something with ConceptMaps having a blank uuids and now getting set, this should have been
		//fixed in later versions
		conceptService.saveConcept(concept);
		List<AuditLog> existingUpdateLogs = auditLogService.getAuditLogs(null, Collections.singletonList(Action.UPDATED),
		    null, null, null, null);
		//for the two concept maps and the concept
		Assert.assertFalse(concept.getDescriptions().isEmpty());
		
		ConceptDescription cd1 = new ConceptDescription("desc1", Locale.ENGLISH);
		cd1.setDateCreated(new Date());
		cd1.setCreator(Context.getAuthenticatedUser());
		concept.addDescription(cd1);
		conceptService.saveConcept(concept);
		
		List<AuditLog> conceptLogs = auditLogService.getAuditLogs(null, Collections.singletonList(Action.UPDATED), null,
		    null, null, null);
		Assert.assertEquals(existingUpdateLogs.size() + 1, conceptLogs.size());
		conceptLogs.removeAll(existingUpdateLogs);
		Assert.assertEquals(1, conceptLogs.size());
		Assert.assertEquals(conceptLogs.get(0).getObjectUuid(), concept.getUuid());
		
		List<AuditLog> descriptionLogs = auditLogService.getAuditLogs(null, Collections.singletonList(Action.CREATED), null,
		    null, null, null);
		Assert.assertEquals(1, descriptionLogs.size());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateAnAuditLogForTheParentObjectWhenAnElementInAChildCollectionIsUpdated() throws Exception {
		Concept concept = conceptService.getConcept(7);
		int originalDescriptionCount = concept.getDescriptions().size();
		Assert.assertTrue(originalDescriptionCount > 0);
		
		concept.getDescription().setDescription("another descr");
		concept = conceptService.saveConcept(concept);
		Assert.assertEquals(originalDescriptionCount, concept.getDescriptions().size());
		
		List<Class<? extends OpenmrsObject>> clazzes = new ArrayList<Class<? extends OpenmrsObject>>();
		clazzes.add(Concept.class);//get only location logs since there those of GPs that we have changed
		List<AuditLog> conceptLogs = auditLogService.getAuditLogs(clazzes, Collections.singletonList(Action.UPDATED), null,
		    null, null, null);
		Assert.assertEquals(1, conceptLogs.size());
		Assert.assertEquals(conceptLogs.get(0).getObjectUuid(), concept.getUuid());
		
		clazzes.remove(Concept.class);
		clazzes.add(ConceptDescription.class);
		List<AuditLog> descriptionLogs = auditLogService.getAuditLogs(clazzes, Collections.singletonList(Action.UPDATED),
		    null, null, null, null);
		Assert.assertEquals(1, descriptionLogs.size());
	}
	
	@Test
	@NotTransactional
	public void shouldUpdateTheMonitoredClassCacheWhenTheMonitoredClassGlobalPropertyIsUpdatedWithAnAddition()
	    throws Exception {
		Assert.assertFalse(AuditLogUtil.getMonitoredClasses().contains(Encounter.class));
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORED_CLASSES);
		Set<Class<?>> monitoredClasses = new HashSet<Class<?>>();
		monitoredClasses.addAll(AuditLogUtil.getMonitoredClasses());
		monitoredClasses.add(Encounter.class);
		gp.setPropertyValue(StringUtils.join(AuditLogUtil.getAsListOfClassnames(monitoredClasses), ","));
		as.saveGlobalProperty(gp);
		Assert.assertTrue(AuditLogUtil.getMonitoredClasses().contains(Encounter.class));
	}
	
	@Test
	@NotTransactional
	public void shouldUpdateTheMonitoredClassCacheWhenTheMonitoredClassGlobalPropertyIsUpdatedWithARemoval()
	    throws Exception {
		Assert.assertTrue(AuditLogUtil.getMonitoredClasses().contains(Concept.class));
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORED_CLASSES);
		Set<Class<?>> monitoredClasses = new HashSet<Class<?>>();
		monitoredClasses.addAll(AuditLogUtil.getMonitoredClasses());
		monitoredClasses.remove(Concept.class);
		gp.setPropertyValue(StringUtils.join(AuditLogUtil.getAsListOfClassnames(monitoredClasses), ","));
		as.saveGlobalProperty(gp);
		Assert.assertFalse(AuditLogUtil.getMonitoredClasses().contains(Concept.class));
	}
	
	@Test
	@NotTransactional
	public void shouldMonitorAnyOpenmrsObjectWhenStrateyIsSetToAll() throws Exception {
		Assert.assertFalse(AuditLogUtil.getMonitoredClasses().contains(Location.class.getName()));
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORING_STRATEGY);
		gp.setPropertyValue(MonitoringStrategy.ALL.name());
		as.saveGlobalProperty(gp);
		Location location = new Location();
		location.setName("new location");
		Context.getLocationService().saveLocation(location);
		List<Class<? extends OpenmrsObject>> clazzes = new ArrayList<Class<? extends OpenmrsObject>>();
		clazzes.add(Location.class);//get only location logs
		Assert.assertEquals(1, auditLogService.getAuditLogs(clazzes, null, null, null, null, null).size());
	}
	
	@Test
	@NotTransactional
	public void shouldNotMonitorAnyObjectWhenStrateyIsSetToNone() throws Exception {
		Assert.assertTrue(AuditLogUtil.getMonitoredClasses().contains(EncounterType.class));
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORING_STRATEGY);
		gp.setPropertyValue(MonitoringStrategy.NONE.name());
		as.saveGlobalProperty(gp);
		EncounterType encounterType = encounterService.getEncounterType(6);
		encounterService.purgeEncounterType(encounterType);
		Assert.assertEquals(0, getAllLogs().size());
	}
	
	@Test
	@NotTransactional
	public void shouldNotCreateLogWhenStrateyIsSetToAllExceptAndObjectTypeIsListedAsExcluded() throws Exception {
		//sanity check
		Assert.assertTrue(OpenmrsUtil.collectionContains(AuditLogUtil.getUnMonitoredClasses(), EncounterType.class));
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORING_STRATEGY);
		gp.setPropertyValue(MonitoringStrategy.ALL_EXCEPT.name());
		as.saveGlobalProperty(gp);
		
		EncounterType encounterType = encounterService.getEncounterType(6);
		encounterService.purgeEncounterType(encounterType);
		List<Class<? extends OpenmrsObject>> clazzes = new ArrayList<Class<? extends OpenmrsObject>>();
		clazzes.add(EncounterType.class);//get only encounter type logs since there those of GPs that we have changed
		Assert.assertEquals(0, auditLogService.getAuditLogs(clazzes, null, null, null, null, null).size());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateLogWhenStrateyIsSetToAllExceptAndObjectTypeIsNotListedAsIncluded() throws Exception {
		//sanity check
		Assert.assertFalse(OpenmrsUtil.collectionContains(AuditLogUtil.getUnMonitoredClasses(), Location.class));
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORING_STRATEGY);
		gp.setPropertyValue(MonitoringStrategy.ALL_EXCEPT.name());
		as.saveGlobalProperty(gp);
		
		Location location = new Location();
		location.setName("new location");
		Context.getLocationService().saveLocation(location);
		List<Class<? extends OpenmrsObject>> clazzes = new ArrayList<Class<? extends OpenmrsObject>>();
		clazzes.add(Location.class);//get only location logs since there those of GPs that we have changed
		Assert.assertEquals(1, auditLogService.getAuditLogs(clazzes, null, null, null, null, null).size());
	}
	
	@Test
	@NotTransactional
	public void shouldCreateLogForUnMonitoredTypeIfTheOwningTypeIsMonitoredAndStrategyIsAllExcept() throws Exception {
		executeDataSet("org/openmrs/api/include/LocationServiceTest-initialData.xml");
		LocationService ls = Context.getLocationService();
		Assert.assertEquals(0, auditLogService.getAuditLogs(null, null, null, null, null, null).size());
		
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(AuditLogConstants.GP_MONITORING_STRATEGY);
		gp.setPropertyValue(MonitoringStrategy.ALL_EXCEPT.name());
		as.saveGlobalProperty(gp);
		
		AuditLogUtil.stopMonitoring(LocationTag.class);
		Location loc = ls.getLocation(2);
		loc.getTags().iterator().next().setDescription("new");
		ls.saveLocation(loc);
		List<Class<? extends OpenmrsObject>> clazzes = new ArrayList<Class<? extends OpenmrsObject>>();
		clazzes.add(Location.class);
		clazzes.add(LocationTag.class);
		Assert.assertEquals(2, auditLogService.getAuditLogs(clazzes, null, null, null, null, null).size());
	}
}
