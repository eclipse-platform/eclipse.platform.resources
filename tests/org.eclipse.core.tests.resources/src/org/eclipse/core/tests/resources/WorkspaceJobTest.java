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
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.core.tests.harness.EclipseWorkspaceTest;

/**
 * Tests implementation of workspace jobs.
 */
public class WorkspaceJobTest extends EclipseWorkspaceTest {
	public static Test suite() {
		return new TestSuite(WorkspaceJobTest.class);
	}
	public WorkspaceJobTest() {
		super();
	}
	public WorkspaceJobTest(String name) {
		super(name);
	}
	public void testSimple() {
		final IProject project = getWorkspace().getRoot().getProject("TestSimple");
		final IFile file = project.getFile("Simple.txt");
		final IFile dotProject = project.getFile(IProjectDescription.DESCRIPTION_FILE_NAME);
		final boolean[] finished = new boolean[] { false };
		final WorkspaceJob job = new WorkspaceJob("testSimple") {
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
				project.create(null);
				project.open(null);
				file.create(getRandomContents(), IResource.NONE, null);
				finished[0] = true;
				return Status.OK_STATUS;
			}
		};
		job.addJobChangeListener(new JobChangeAdapter() {
			public void done(IJobChangeEvent event) {
				assertTrue("1.0", event.getJob() == job);
				assertTrue("1.1", event.getResult().isOK());
				assertTrue("1.2", finished[0]);
			}
		});
		//listener to ensure only one resource change occurs.
		waitForNotify();
		ResourceDeltaVerifier verifier = new ResourceDeltaVerifier();
		getWorkspace().addResourceChangeListener(verifier);
		try {
			assertTrue("2.0", !verifier.hasBeenNotified());
			verifier.addExpectedChange(project, IResourceDelta.ADDED, IResourceDelta.OPEN);
			verifier.addExpectedChange(dotProject, IResourceDelta.ADDED, IResource.NONE);
			verifier.addExpectedChange(file, IResourceDelta.ADDED, IResource.NONE);
			job.schedule();
			job.join();
			waitForNotify();
			assertTrue("2.1", verifier.hasBeenNotified());
			assertTrue(verifier.getMessage(), verifier.isDeltaValid());
		} catch (InterruptedException e) {
			fail("4.99", e);
		} finally {
			getWorkspace().removeResourceChangeListener(verifier);
		}
	}
}