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
import org.eclipse.core.internal.watson.ElementTree;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Job for performing notifications to workspace resource change listeners.
 */
public class ListenerNotifyJob extends Job {
	/**
	 * The maximum time to wait, in milliseconds, before starting a notification.
	 */
	private static final long WAIT_TIMEOUT = 10000;//10 seconds
	private Workspace workspace;
	private long pendingStart;
	private boolean isPending = false;
	public ListenerNotifyJob(Workspace workspace ) {
		this.workspace = workspace;
	}
	public IStatus run(IProgressMonitor monitor) {
		ElementTree tree = workspace.getElementTree();
		boolean wasImmutable = tree.isImmutable();
		workspace.broadcastChanges(tree, IResourceChangeEvent.POST_CHANGE, true, true, Policy.monitorFor(null));
		workspace.getMarkerManager().resetMarkerDeltas();
		tree = workspace.getElementTree();
		//make sure the workspace mutability is the same as when we started the notification
		if (wasImmutable) {
			if (!tree.isImmutable())
				tree.immutable();
		} else {
			if (tree.isImmutable())
				workspace.newWorkingTree();
		}

		return Status.OK_STATUS;
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
			if (System.currentTimeMillis() - pendingStart > WAIT_TIMEOUT) {
				isPending = false;
				schedule();
			}
		} else {
			isPending = true;
			pendingStart = System.currentTimeMillis();
		}
	}
	/**
	 * A top level workspace modifying operation has finished.
	 */
	public void endTopLevel() {
		//if a notification is already queued, do nothing
		if (getState() == Job.WAITING)
			return;
		isPending = false;
		schedule();
	}
}
