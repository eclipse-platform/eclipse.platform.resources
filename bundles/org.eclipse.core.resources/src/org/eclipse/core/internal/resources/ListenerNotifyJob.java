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

import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Job for performing POST_CHANGE notifications to workspace resource change listeners.
 */
public class ListenerNotifyJob extends Job {
	/**
	 * The maximum time to wait, in milliseconds, before starting a notification.
	 */
	private static final long MAX_DELAY= 10000; //10 seconds
	/**
	 * The minimum time to wait, in milliseconds, before starting a notification.
	 */
	private static final long MIN_DELAY = 1000; //1 second
	private boolean isPending = false;
	private long pendingStart;
	private long lastFinished;
	private Workspace workspace;

	public ListenerNotifyJob(Workspace workspace) {
		super(ICoreConstants.MSG_RESOURCES_UPDATING);
		this.workspace = workspace;
		this.lastFinished = System.currentTimeMillis();
	}
	private void basicRun(IProgressMonitor monitor) throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask(ICoreConstants.MSG_RESOURCES_UPDATING, Policy.totalWork);
			try {
				workspace.prepareOperation();
				workspace.beginOperation(false);
				workspace.broadcastChanges(IResourceChangeEvent.POST_CHANGE, true);
				monitor.worked(Policy.opWork);
			} finally {
				workspace.endOperation(false, Policy.subMonitorFor(monitor, Policy.buildWork));
			}
			//notify the auto-build job that notification is done
			workspace.autoBuildJob.endNotify();
		} finally {
			monitor.done();
		}
	}
	/**
	 * Performs scheduling, and ensures that notification doesn't happen too often.
	 * There must be at least MIN_DELAY ms between end of last notification and
	 * start of next one
	 * @param currentTime the time right now, in milliseconds
	 */
	private void doSchedule(long currentTime)  {
		isPending = false;
		schedule(Math.max(0L, (lastFinished + MIN_DELAY) - currentTime));
	}
	/**
	 * A nested workspace modifying operation has finished.
	 */
	public void endNested() {
		//if a notification is already queued, do nothing
		if (getState() == Job.WAITING)
			return;
		//if we've been pending too long, start a notification job anyway
		if (isPending) {
			long currentTime = System.currentTimeMillis();
			if (currentTime - pendingStart > MAX_DELAY)
				doSchedule(currentTime);
		} else {
			isPending = true;
			pendingStart = System.currentTimeMillis();
		}
	}
	/**
	 * A top level workspace modifying operation has finished.
	 */
	public void endTopLevel() {
		//if a notification is already queued or running, do nothing
		switch (getState()) {
			case WAITING :
			case RUNNING :
				return;
		}
		doSchedule(System.currentTimeMillis());
	}
	/* (non-Javadoc)
	 * @see Job#run
	 */
	public IStatus run(IProgressMonitor monitor) {
		try {
			basicRun(monitor);
			return Status.OK_STATUS;
		} catch (CoreException e) {
			return e.getStatus();
		} finally {
			lastFinished = System.currentTimeMillis();
		}
	}
}