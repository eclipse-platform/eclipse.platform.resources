/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.resources;

import org.eclipse.core.internal.jobs.OrderedLock;
import org.eclipse.core.internal.utils.Assert;
import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.runtime.*;
/**
 * Used to track operation state for each thread that is involved in an operation.
 * This includes prepared and running operation depth, auto-build strategy and
 * cancel state.
 */
public class WorkManager implements IManager {

	public static final int OPERATION_EMPTY = 0;
	public static final int OPERATION_NONE = -1;

	protected final OrderedLock lock;
	private int nestedOperations = 0;
	private boolean operationCanceled = false;
	private int preparedOperations = 0;
	/**
	 * Indicates whether any operations have run that may require a build.
	 */
	private boolean hasBuildChanges = false;

	public WorkManager() {
		this.lock = (OrderedLock) Platform.getJobManager().newLock();
	}
	/**
	 * An operation calls this method and it only returns when the operation
	 * is free to run.
	 */
	public void checkIn() throws CoreException {
		try {
			lock.acquire();
		} finally {
			incrementPreparedOperations();
		}
	}
	/**
	 * Inform that an operation has finished.
	 */
	public synchronized void checkOut() throws CoreException {
		decrementPreparedOperations();
		rebalanceNestedOperations();
		//reset state if this is the end of a top level operation
		if (preparedOperations == 0) {
			operationCanceled = false;
			hasBuildChanges = false;
		}
		//lock keeps its own counter to decide when to release
		lock.release();
	}
	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	private void decrementPreparedOperations() {
		preparedOperations--;
	}
	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	public synchronized int getPreparedOperationDepth() {
		return preparedOperations;
	}
	/**
	 * Indicates if the operation that has just completed may potentially 
	 * require a build.
	 */
	public void setBuild(boolean hasChanges)  {
		hasBuildChanges = hasBuildChanges || hasChanges;
	}
	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	void incrementNestedOperations() {
		nestedOperations++;
	}
	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	private void incrementPreparedOperations() {
		preparedOperations++;
	}
	/**
	 * Returns true if the nested operation depth is the same
	 * as the prepared operation depth, and false otherwise.
	 *
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	boolean isBalanced() {
		return nestedOperations == preparedOperations;
	}
	/**
	 * This method is synchronized with checkIn() and checkOut() that use blocks
	 * like synchronized (this) { ... }.
	 */
	public synchronized boolean isCurrentOperation() {
		return lock.getCurrentOperationThread() == Thread.currentThread();
	}
	/**
	 * Re-acquires the workspace lock that was temporarily released during an
	 * operation.  
	 * @see unlockTree
	 */
	public void lockTree() {
		int depth = getPreparedOperationDepth();
		for (int i = 0; i < depth; i++)
			lock.acquire();
	}

	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	public void operationCanceled() {
		operationCanceled = true;
	}
	/**
	 * Used to make things stable again after an operation has failed between
	 * a workspace.prepareOperation() and workspace.beginOperation().
	 * 
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a 
	 * prepareOperation/endOperation block.
	 */
	public void rebalanceNestedOperations() {
		nestedOperations = preparedOperations;
	}

	/**
	 * This method can only be safely called from inside a workspace operation.
	 * Should NOT be called from outside a prepareOperation/endOperation block.
	 */
	public boolean shouldBuild() {
		if (hasBuildChanges) {
			if (operationCanceled)
				return Policy.buildOnCancel;
			return true;
		}
		return false;
	}
	public void shutdown(IProgressMonitor monitor) {
	}
	public void startup(IProgressMonitor monitor) {
	}
	/**
	 * Releases the workspace lock without changing the nested operation depth.
	 * Must be followed eventually by lockTree.
	 * @see lockTree
	 */
	public void unlockTree() {
		int depth = lock.getDepth();
		Assert.isTrue(depth == getPreparedOperationDepth(), "Lock depth does not match operation depth"); //$NON-NLS-1$
		for (int i = 0; i < depth; i++)
			lock.release();
	}
}