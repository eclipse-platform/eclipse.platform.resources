/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.tests.resources;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.tests.harness.EclipseWorkspaceTest;

/**
 * Tests involving a resource change listener that makes changes to the
 * workspace.
 */
public class ChangingListenerTest extends EclipseWorkspaceTest {
	protected boolean wasAutoBuilding;

	public static Test suite() {
		return new TestSuite(ChangingListenerTest.class);
	}
	public ChangingListenerTest() {
		super();
	}
	public ChangingListenerTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		super.setUp();
		IWorkspaceDescription description = getWorkspace().getDescription();
		wasAutoBuilding = description.isAutoBuilding();
		if (wasAutoBuilding) {
			description.setAutoBuilding(false);
			getWorkspace().setDescription(description);
		}
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		if (wasAutoBuilding) {
			IWorkspaceDescription description = getWorkspace().getDescription();
			description.setAutoBuilding(true);
			getWorkspace().setDescription(description);
		}
	}
	public void testChangeInPreClose() {
		final IProject project = getWorkspace().getRoot().getProject("Project");
		final IFile file = project.getFile("abc.txt");
		class Listener extends ResourceDeltaVerifier {
			public void resourceChanged(IResourceChangeEvent event) {
				assertEquals("1.0", IResourceChangeEvent.PRE_CLOSE, event.getType());
				assertEquals("1.1", project, event.getResource());
				//ensure that I'm allowed to modify the workspace
				try {
					file.create(getRandomContents(), IResource.NONE, getMonitor());
				} catch (CoreException e) {
					ChangingListenerTest.this.fail("1.99", e);
				}
			}
		}
		ensureExistsInWorkspace(project, true);
		Listener listener = new Listener();
		getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.PRE_CLOSE);
		try {
			assertTrue("2.0", !file.exists());
			project.close(getMonitor());
			project.open(getMonitor());
			assertTrue("2.1", file.exists());
		} catch (CoreException e) {
			fail("2.99", e);
		} finally {
			getWorkspace().removeResourceChangeListener(listener);
		}
	}
	public void testChangeInPreDelete() {
		final IProject project = getWorkspace().getRoot().getProject("Project");
		final IFile file = project.getFile("abc.txt");
		class Listener extends ResourceDeltaVerifier {
			public void resourceChanged(IResourceChangeEvent event) {
				assertEquals("1.0", IResourceChangeEvent.PRE_DELETE, event.getType());
				assertEquals("1.1", project, event.getResource());
				//ensure that I'm allowed to modify the workspace
				try {
					file.create(getRandomContents(), IResource.NONE, getMonitor());
				} catch (CoreException e) {
					ChangingListenerTest.this.fail("1.99", e);
				}
			}
		}
		ensureExistsInWorkspace(project, true);
		Listener listener = new Listener();
		getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.PRE_DELETE);
		try {
			assertTrue("2.0", !file.exists());
			project.delete(IResource.NEVER_DELETE_PROJECT_CONTENT, getMonitor());
			project.create(getMonitor());
			project.open(getMonitor());
			assertTrue("2.1", file.exists());
		} catch (CoreException e) {
			fail("2.99", e);
		} finally {
			getWorkspace().removeResourceChangeListener(listener);
		}
	}
	public void testCreateFile() {
		final IProject project = getWorkspace().getRoot().getProject("Project");
		final IFile dotProject = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		final IFile file = project.getFile("File.txt");
		/**
		 * Listener that modifies a file on its first iteration, and then checks that
		 * it receives its own delta.
		 */
		class Listener1 extends ResourceDeltaVerifier {
			int iteration = 0;
			public void resourceChanged(IResourceChangeEvent event) {
				switch (iteration) {
					case 0 :
						super.reset();
						addExpectedChange(project, IResourceDelta.ADDED, IResourceDelta.OPEN);
						addExpectedChange(dotProject, IResourceDelta.ADDED, 0);
						super.verifyDelta(event.getDelta());
						assertTrue("1.0: " + getMessage(), isDeltaValid());
						try {
							file.create(getRandomContents(), IResource.NONE, getMonitor());
						} catch (CoreException e) {
							ChangingListenerTest.this.fail("1.99", e);
						}
						break;
					case 1 :
						super.reset();
						addExpectedChange(file, IResourceDelta.ADDED, 0);
						super.verifyDelta(event.getDelta());
						assertTrue("2.0: " + getMessage(), isDeltaValid());
						break;
				}
				iteration++;
			}
		}
		Listener1 listener = new Listener1();
		waitForNotify();
		getWorkspace().addResourceChangeListener(listener);
		try {
			ensureExistsInWorkspace(project, true);
			//wait for a notification to pass
			waitForNotify();
			assertTrue("3.0", file.exists());
			//wait for second notification
			while (listener.iteration < 2)
				waitForNotify();
		} finally {
			getWorkspace().removeResourceChangeListener(listener);
		}
	}
}
