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
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Job for performing POST_CHANGE notifications to workspace resource change listeners.
 */
public class ListenerNotifyJob extends Job implements IJobChangeListener {
	private boolean isPending = false;
	private long lastFinished;
	private long maxDelay;
	private long minDelay;
	private long pendingStart;
	private boolean restartNeeded = false;
	private Workspace workspace;

	public ListenerNotifyJob(Workspace workspace) {
		super(ICoreConstants.MSG_RESOURCES_UPDATING);
		this.workspace = workspace;
		this.lastFinished = System.currentTimeMillis();
		this.maxDelay = Policy.defaultMaxNotifyDelay;
		this.minDelay = Policy.defaultMinNotifyDelay;
		addJobChangeListener(this);
	}
	private void basicRun(IProgressMonitor monitor) throws CoreException {
		monitor = Policy.monitorFor(monitor);
		try {
			monitor.beginTask(null, Policy.totalWork);
			try {
				workspace.prepareOperation();
				workspace.beginOperation(false);
				workspace.broadcastChanges(IResourceChangeEvent.POST_CHANGE, true, true);
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
	public boolean belongsTo(Object family) {
		return family == this;
	}
	/**
	 * Performs scheduling, and ensures that notification doesn't happen too often.
	 * There must be at least MIN_DELAY ms between end of last notification and
	 * start of next one
	 * @param currentTime the time right now, in milliseconds
	 */
	private void doSchedule(long currentTime)  {
		isPending = restartNeeded = false;
		schedule(Math.max(0L, (lastFinished + minDelay) - currentTime));
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
			if (currentTime - pendingStart > maxDelay)
				doSchedule(currentTime);
		} else {
			isPending = true;
			pendingStart = System.currentTimeMillis();
		}
	}
	/**
	 * A top level workspace modifying operation has finished.
	 */
	public void endTopLevel(boolean hasTreeChanges) {
		//only schedule a job if there is not one already running, waiting, or sleeping
		switch (getState()) {
			case NONE:
				doSchedule(System.currentTimeMillis());
				break;
			case RUNNING:
				restartNeeded = hasTreeChanges;
				break;
		}
	}
	/* (non-Javadoc)
	 * @see Job#run
	 */
	public IStatus run(IProgressMonitor monitor) {
		try {
			basicRun(monitor);
			//recompute max/min delay in case they have changed
			maxDelay = workspace.internalGetDescription().getMaxNotifyDelay();
			minDelay = workspace.internalGetDescription().getMinNotifyDelay();
			return Status.OK_STATUS;
		} catch (CoreException e) {
			return e.getStatus();
		} finally {
			lastFinished = System.currentTimeMillis();
		}
	}
	public void aboutToRun(IJobChangeEvent event) {
	}
	public void awake(IJobChangeEvent event) {
	}
	public void done(IJobChangeEvent event) {
		if (restartNeeded)
			doSchedule(System.currentTimeMillis());
	}
	public void running(IJobChangeEvent event) {
	}
	public void scheduled(IJobChangeEvent event) {
	}
	public void sleeping(IJobChangeEvent event) {
	}
}