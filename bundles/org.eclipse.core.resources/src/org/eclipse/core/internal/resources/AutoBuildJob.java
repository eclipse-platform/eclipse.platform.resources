/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.internal.resources;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;

/**
 * The job for performing workspace auto-builds.
 */
class AutoBuildJob extends Job {
	private final Workspace workspace;
	AutoBuildJob(Workspace workspace) {
		this.workspace = workspace;
	}
	public IStatus run(IProgressMonitor monitor) {
		if (monitor.isCanceled())
			return Status.CANCEL_STATUS;
		try {
			workspace.build(IncrementalProjectBuilder.AUTO_BUILD, monitor);
			return Status.OK_STATUS;
		} catch (OperationCanceledException e) {
			return Status.CANCEL_STATUS;
		} catch (CoreException sig) {
			return sig.getStatus();
		}
	}
	public void checkCancel() {
		//cancel the build job if another job is attempting to modify the workspace
		if (Platform.getJobManager().currentJob() != this)
			cancel();
	}
}