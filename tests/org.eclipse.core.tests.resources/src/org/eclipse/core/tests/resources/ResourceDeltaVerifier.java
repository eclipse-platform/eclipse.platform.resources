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
package org.eclipse.core.tests.resources;

import java.util.Enumeration;
import java.util.Hashtable;

import junit.framework.Assert;
import org.eclipse.core.internal.jobs.JobManager;
import org.eclipse.core.internal.resources.TestingSupport;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 * Verifies the state of an <code>IResourceDelta</code> by comparing
 * it with a client's expectations.  The delta is considered valid
 * if it contains exactly the set of changes expected by the client, 
 * and parents of those changes.
 *
 * <p>Example usage:
 * <code>
 * ResourceDeltaVerifier verifier = new ResourceDeltaComparer();
 * IResourceChangeListener listener = (IResourceChangeListener)verifier;
 * IWorkspace workspace = ResourcesPlugin.getWorkspace();
 * IProject proj = workspace.getRoot().getProject("MyProject");
 * // Assume the project is accessible
 * workspace.addResourceChangeListener(listener);
 * verifier.addExpectedChange(proj, REMOVED, 0);
 * try {
 * 		proj.delete(true, true, null);
 * } catch(CoreException e){
 *     fail("1.0", e);
 * }
 * assert("2.0 "+verifier.getMessage(), verifier.isDeltaValid());
 * </code>
 */
public class ResourceDeltaVerifier extends Assert implements IResourceChangeListener {
	private static final boolean DEBUG = false;
	private class ExpectedChange {
		int changeFlags;
		int kind;
		IResource resource;
		IPath movedFromPath;
		IPath movedToPath;

		public ExpectedChange(IResource resource, int kind, int changeFlags, IPath movedFromPath, IPath movedToPath) {
			this.resource = resource;
			this.kind = kind;
			this.changeFlags = changeFlags;
			this.movedFromPath = movedFromPath;
			this.movedToPath = movedToPath;
		}
		public int getChangeFlags() {
			return changeFlags;
		}
		public int getKind() {
			return kind;
		}
		public IPath getMovedFromPath() {
			if ((changeFlags & IResourceDelta.MOVED_FROM) != 0) {
				return movedFromPath;
			} else {
				return null;
			}
		}
		public IPath getMovedToPath() {
			if ((changeFlags & IResourceDelta.MOVED_TO) != 0) {
				return movedToPath;
			} else {
				return null;
			}
		}
		public IResource getResource() {
			return this.resource;
		}
		public String toString() {
			StringBuffer buf = new StringBuffer("ExpectedChange(");
			buf.append(this.resource);
			buf.append(", ");
			buf.append(convertKind(kind));
			buf.append(", ");
			buf.append(convertChangeFlags(changeFlags));
			buf.append(")");
			return buf.toString();
		}
	}
	/**
	 * The verifier can be in one of three states.  In the initial
	 * state, the verifier is still receiving inputs via the
	 * addExpectedChange() methods, and the state is RECEIVING_INPUTS.
	 * After a call to verifyDelta(), the state becomes DELTA_VERIFIED
	 * The verifier remains in the second state for any number of delta
	 * verifications.  When a getMessage() or isDeltaValid() method is
	 * called, the verification completes, and the state becomes
	 * VERIFICATION_COMPLETE.  While in this state, any number of
	 * getMessage() and isDeltaValid() methods can be called.  
	 * While in the third state, any call to addExpectedChange()
	 * resets the verifier and puts it back in its RECEIVING_INPUTS state.
	 */
	private static final int RECEIVING_INPUTS = 0;
	private static final int DELTA_VERIFIED = 1;
	private static final int VERIFICATION_COMPLETE = 2;

	/**
	 * Table of IPath -> ExpectedChange
	 */
	private Hashtable expectedChanges = new Hashtable();
	boolean isDeltaValid = true;
	private StringBuffer message = new StringBuffer();

	private int state = RECEIVING_INPUTS;
	public static void debug(String msg) {
		if (DEBUG)
			JobManager.debug(msg);
	}

	public ResourceDeltaVerifier() {
	}
	/**
	 * Signals to the comparer that the given resource is expected to
	 * change in the specified way.  The change flags should be set to
	 * zero if no change is expected.
	 * @param resource the resource that is expected to change
	 * @param status the type of change (ADDED, REMOVED, CHANGED)
	 * @param changeFlags the type of change (CONTENT, SYNC, etc)
	 * @see IResourceConstants
	 */
	public void addExpectedChange(IResource resource, int status, int changeFlags) {
		addExpectedChange(resource, null, status, changeFlags, null, null);
	}
	/**
	 * Signals to the comparer that the given resource is expected to
	 * change in the specified way.  The change flags should be set to
	 * zero if no change is expected.
	 * @param resource the resource that is expected to change
	 * @param status the type of change (ADDED, REMOVED, CHANGED)
	 * @param changeFlags the type of change (CONTENT, SYNC, etc)
	 * @param movedPath or null
	 * @see IResourceConstants
	 */
	public void addExpectedChange(IResource resource, int status, int changeFlags, IPath movedFromPath, IPath movedToPath) {
		addExpectedChange(resource, null, status, changeFlags, movedFromPath, movedToPath);
	}
	/**
	 * Signals to the comparer that the given resource is expected to
	 * change in the specified way.  The change flags should be set to
	 * zero if no change is expected.
	 * @param resource the resource that is expected to change
	 * @param topLevelParent Do not added expected changes above this parent
	 * @param status the type of change (ADDED, REMOVED, CHANGED)
	 * @param changeFlags the type of change (CONTENT, SYNC, etc)
	 * @param movedPath or null
	 * @see IResourceConstants
	 */
	public void addExpectedChange(IResource resource, IResource topLevelParent, int status, int changeFlags) {
		addExpectedChange(resource, topLevelParent, status, changeFlags, null, null);
	}
	/**
	 * Signals to the comparer that the given resource is expected to
	 * change in the specified way.  The change flags should be set to
	 * zero if no change is expected.
	 * @param resource the resource that is expected to change
	 * @param topLevelParent Do not added expected changes above this parent
	 * @param status the type of change (ADDED, REMOVED, CHANGED)
	 * @param changeFlags the type of change (CONTENT, SYNC, etc)
	 * @param movedPath or null
	 * @see IResourceConstants
	 */
	public void addExpectedChange(IResource resource, IResource topLevelParent, int status, int changeFlags, IPath movedFromPath, IPath movedToPath) {
		resetIfNecessary();

		ExpectedChange expectedChange = new ExpectedChange(resource, status, changeFlags, movedFromPath, movedToPath);
		expectedChanges.put(resource.getFullPath(), expectedChange);

		// Add changes for all resources above this one and limited by the topLevelParent
		IResource parentResource = resource.getParent();
		IResource limit = (topLevelParent == null) ? null : topLevelParent.getParent();
		while (parentResource != null && !parentResource.equals(limit)) {
			//change table is keyed by resource path
			IPath key = parentResource.getFullPath();
			if (expectedChanges.get(key) == null) {
				ExpectedChange parentExpectedChange = new ExpectedChange(parentResource, IResourceDelta.CHANGED, 0, null, null);
				expectedChanges.put(key, parentExpectedChange);
			}
			parentResource = parentResource.getParent();
		}
	}
	/**
	 * @see #addExpectedChange
	 */
	public void addExpectedChange(IResource[] resources, int status, int changeFlags) {
		for (int i = 0; i < resources.length; i++)
			addExpectedChange(resources[i], null, status, changeFlags, null, null);
	}
	/**
	 * Adds an expected deletion for the given resource and all children.
	 */
	public void addExpectedDeletion(IResource resource) {
		addExpectedChange(resource, IResourceDelta.REMOVED, 0);
		if (resource instanceof IContainer) {
			try {
				IResource[] children = ((IContainer) resource).members(IContainer.INCLUDE_PHANTOMS | IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);
				for (int i = 0; i < children.length; i++) {
					addExpectedDeletion(children[i]);
				}
			} catch (CoreException e) {
				e.printStackTrace();
				fail("Failed to get children in addExpectedDeletion");
			}
		}
	}
	private void checkChanges(IResourceDelta delta) {
		IResource resource = delta.getResource();

		ExpectedChange expectedChange = (ExpectedChange) expectedChanges.remove(resource.getFullPath());

		int status = delta.getKind();
		int changeFlags = delta.getFlags();

		if (status == IResourceDelta.NO_CHANGE)
			return;

		if (expectedChange == null) {
			recordMissingExpectedChange(status, changeFlags);
		} else {
			int expectedStatus = expectedChange.getKind();
			int expectedChangeFlags = expectedChange.getChangeFlags();
			if (status != expectedStatus || changeFlags != expectedChangeFlags) {
				recordConflictingChange(expectedStatus, status, expectedChangeFlags, changeFlags);
			}
		}
	}
	private void checkChildren(IResourceDelta delta) {
		IResourceDelta[] affectedChildren = delta.getAffectedChildren(IResourceDelta.ALL_WITH_PHANTOMS, IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);
		IResourceDelta[] addedChildren = delta.getAffectedChildren(IResourceDelta.ADDED, IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);
		IResourceDelta[] changedChildren = delta.getAffectedChildren(IResourceDelta.CHANGED, IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);
		IResourceDelta[] removedChildren = delta.getAffectedChildren(IResourceDelta.REMOVED, IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);

		Hashtable h = new Hashtable(affectedChildren.length + 1);

		for (int i = 0; i < addedChildren.length; ++i) {
			IResourceDelta childDelta1 = addedChildren[i];
			IResource childResource = childDelta1.getResource();
			IResourceDelta childDelta2 = (IResourceDelta) h.get(childResource);
			if (childDelta2 != null) {
				recordDuplicateChild(childResource.getFullPath(), childDelta2.getKind(), childDelta1.getKind(), IResourceDelta.ADDED);
			} else {
				h.put(childResource, childDelta1);
			}
			if (childDelta1.getKind() != IResourceDelta.ADDED) {
				recordIllegalChild(childResource.getFullPath(), IResourceDelta.ADDED, childDelta1.getKind());
			}
		}

		for (int i = 0; i < changedChildren.length; ++i) {
			IResourceDelta childDelta1 = changedChildren[i];
			IResource childResource = childDelta1.getResource();
			IResourceDelta childDelta2 = (IResourceDelta) h.get(childResource);
			if (childDelta2 != null) {
				recordDuplicateChild(childResource.getFullPath(), childDelta2.getKind(), childDelta1.getKind(), IResourceDelta.CHANGED);
			} else {
				h.put(childResource, childDelta1);
			}
			if (childDelta1.getKind() != IResourceDelta.CHANGED) {
				recordIllegalChild(childResource.getFullPath(), IResourceDelta.CHANGED, childDelta1.getKind());
			}
		}

		for (int i = 0; i < removedChildren.length; ++i) {
			IResourceDelta childDelta1 = removedChildren[i];
			IResource childResource = childDelta1.getResource();
			IResourceDelta childDelta2 = (IResourceDelta) h.get(childResource);
			if (childDelta2 != null) {
				recordDuplicateChild(childResource.getFullPath(), childDelta2.getKind(), childDelta1.getKind(), IResourceDelta.REMOVED);
			} else {
				h.put(childResource, childDelta1);
			}
			if (childDelta1.getKind() != IResourceDelta.REMOVED) {
				recordIllegalChild(childResource.getFullPath(), IResourceDelta.REMOVED, childDelta1.getKind());
			}
		}

		for (int i = 0; i < affectedChildren.length; ++i) {
			IResourceDelta childDelta1 = affectedChildren[i];
			IResource childResource = childDelta1.getResource();
			IResourceDelta childDelta2 = (IResourceDelta) h.remove(childResource);
			if (childDelta2 == null) {
				int kind = childDelta1.getKind();
				//these kinds should have been added to h earlier
				if (kind == IResourceDelta.ADDED || kind == IResourceDelta.REMOVED || kind == IResourceDelta.CHANGED) {
					recordMissingChild(childResource.getFullPath(), childDelta1.getKind(), false);
				}
			}
		}

		Enumeration keys = h.keys();
		while (keys.hasMoreElements()) {
			IResource childResource = (IResource) keys.nextElement();
			IResourceDelta childDelta = (IResourceDelta) h.get(childResource);
			recordMissingChild(childResource.getFullPath(), childDelta.getKind(), true);
		}

		for (int i = 0; i < affectedChildren.length; ++i) {
			internalVerifyDelta(affectedChildren[i]);
		}

		keys = h.keys();
		while (keys.hasMoreElements()) {
			IResource childResource = (IResource) keys.nextElement();
			IResourceDelta childDelta = (IResourceDelta) h.get(childResource);
			internalVerifyDelta(childDelta);
		}
	}
	private void checkPaths(IResourceDelta delta) {
		IResource resource = delta.getResource();

		IPath expectedFullPath = resource.getFullPath();
		IPath actualFullPath = delta.getFullPath();
		if (!expectedFullPath.equals(actualFullPath)) {
			recordConflictingFullPaths(expectedFullPath, actualFullPath);
		}

		IPath expectedProjectRelativePath = resource.getProjectRelativePath();
		IPath actualProjectRelativePath = delta.getProjectRelativePath();
		if (expectedProjectRelativePath != actualProjectRelativePath) {
			if (expectedProjectRelativePath == null || !expectedProjectRelativePath.equals(actualProjectRelativePath)) {
				recordConflictingProjectRelativePaths(expectedProjectRelativePath, actualProjectRelativePath);
			}
		}

		ExpectedChange expectedChange = (ExpectedChange) expectedChanges.get(resource.getFullPath());

		if (expectedChange != null) {
			IPath expectedMovedFromPath = expectedChange.getMovedFromPath();
			IPath actualMovedFromPath = delta.getMovedFromPath();
			if (expectedMovedFromPath != actualMovedFromPath) {
				if (expectedMovedFromPath == null || !expectedMovedFromPath.equals(actualMovedFromPath)) {
					recordConflictingMovedFromPaths(expectedMovedFromPath, actualMovedFromPath);
				}
			}

			IPath expectedMovedToPath = expectedChange.getMovedToPath();
			IPath actualMovedToPath = delta.getMovedToPath();
			if (expectedMovedToPath != actualMovedToPath) {
				if (expectedMovedToPath == null || !expectedMovedToPath.equals(actualMovedToPath)) {
					recordConflictingMovedToPaths(expectedMovedToPath, actualMovedToPath);
				}
			}
		}
	}
	String convertChangeFlags(int changeFlags) {
		if (changeFlags == 0) {
			return "0";
		}
		StringBuffer buf = new StringBuffer();

		if ((changeFlags & IResourceDelta.CONTENT) != 0) {
			changeFlags ^= IResourceDelta.CONTENT;
			buf.append("CONTENT | ");
		}
		if ((changeFlags & IResourceDelta.MOVED_FROM) != 0) {
			changeFlags ^= IResourceDelta.MOVED_FROM;
			buf.append("MOVED_FROM | ");
		}
		if ((changeFlags & IResourceDelta.MOVED_TO) != 0) {
			changeFlags ^= IResourceDelta.MOVED_TO;
			buf.append("MOVED_TO | ");
		}
		if ((changeFlags & IResourceDelta.OPEN) != 0) {
			changeFlags ^= IResourceDelta.OPEN;
			buf.append("OPEN | ");
		}
		if ((changeFlags & IResourceDelta.TYPE) != 0) {
			changeFlags ^= IResourceDelta.TYPE;
			buf.append("TYPE | ");
		}
		if ((changeFlags & IResourceDelta.MARKERS) != 0) {
			changeFlags ^= IResourceDelta.MARKERS;
			buf.append("MARKERS | ");
		}
		if ((changeFlags & IResourceDelta.REPLACED) != 0) {
			changeFlags ^= IResourceDelta.REPLACED;
			buf.append("REPLACED | ");
		}

		if (changeFlags != 0) {
			buf.append(changeFlags);
			buf.append(" | ");
		}

		String result = buf.toString();

		if (result.length() != 0) {
			result = result.substring(0, result.length() - 3);
		}

		return result;
	}
	String convertKind(int kind) {
		switch (kind) {
			case IResourceDelta.ADDED :
				return "ADDED";
			case IResourceDelta.CHANGED :
				return "CHANGED";
			case IResourceDelta.REMOVED :
				return "REMOVED";
			case IResourceDelta.ADDED_PHANTOM :
				return "ADDED_PHANTOM";
			case IResourceDelta.REMOVED_PHANTOM :
				return "REMOVED_PHANTOM";
			default :
				return "Unknown(" + kind + ")";
		}
	}
	/**
	 * Called to cleanup internal state and make sure expectations
	 * are met after iterating over a resource delta.
	 */
	private void finishVerification() {
		Hashtable resourcePaths = new Hashtable();

		Enumeration keys = expectedChanges.keys();
		while (keys.hasMoreElements()) {
			Object key = keys.nextElement();
			resourcePaths.put(key, key);
		}

		keys = resourcePaths.keys();
		while (keys.hasMoreElements()) {
			IPath resourcePath = (IPath) keys.nextElement();

			message.append("Checking expectations for ");
			message.append(resourcePath);
			message.append("\n");

			ExpectedChange expectedChange = (ExpectedChange) expectedChanges.remove(resourcePath);
			if (expectedChange != null) {
				recordMissingActualChange(expectedChange.getKind(), expectedChange.getChangeFlags());
			}
		}
	}
	/**
	 * Returns a message that describes the result of the resource
	 * delta verification checks.
	 */
	public String getMessage() {
		if (state == RECEIVING_INPUTS) {
			if (hasExpectedChanges()) {
				fail("Verifier has not yet been given a resource delta");
			} else {
				setState(DELTA_VERIFIED);
			}
		}
		if (state == DELTA_VERIFIED) {
			finishVerification();
			setState(VERIFICATION_COMPLETE);
		}
		return message.toString();
	}
	/**
	 * Returns true if this verifier has received a delta notification
	 * since the last reset, and false otherwise.
	 */
	public boolean hasBeenNotified() {
		return state == DELTA_VERIFIED;
	}
	/**
	 * Returns true if this verifier currently has an expected
	 * changes, and false otherwise.
	 */
	public boolean hasExpectedChanges() {
		return !expectedChanges.isEmpty();
	}
	/**
	 * Compares the given delta with the expected changes.  Recursively
	 * compares child deltas.
	 */
	void internalVerifyDelta(IResourceDelta delta) {
		try {
			// FIXME: bogus
			if (delta == null)
				return;
			message.append("Verifying delta for ");
			message.append(delta.getFullPath());
			message.append("\n");

			/* don't check changes for the workspace */
			if (delta.getResource() != null) {
				checkPaths(delta);
				checkChanges(delta);
			}
			checkChildren(delta);
		} catch (Exception e) {
			e.printStackTrace();
			message.append("Exception during event notification:" + e.getMessage());
			isDeltaValid = false;
		}
	}
	/**
	 * Returns whether the resource delta passed all verification
	 * checks.
	 */
	public boolean isDeltaValid() {
		if (state == RECEIVING_INPUTS) {
			if (hasExpectedChanges()) {
				fail("Verifier has not yet been given a resource delta");
			} else {
				setState(DELTA_VERIFIED);
			}
		}
		if (state == DELTA_VERIFIED) {
			finishVerification();
			setState(VERIFICATION_COMPLETE);
		}
		return isDeltaValid;
	}
	private void recordConflictingChange(int expectedKind, int kind, int expectedChangeFlags, int changeFlags) {
		isDeltaValid = false;

		message.append("\tConflicting change\n");

		if (expectedKind != kind) {
			message.append("\t\tExpected kind: <");
			message.append(convertKind(expectedKind));
			message.append("> actual kind: <");
			message.append(convertKind(kind));
			message.append(">\n");
		}

		if (expectedChangeFlags != changeFlags) {
			message.append("\t\tExpected change flags: <");
			message.append(convertChangeFlags(expectedChangeFlags));
			message.append("> actual change flags: <");
			message.append(convertChangeFlags(changeFlags));
			message.append(">\n");
		}
	}
	private void recordConflictingFullPaths(IPath expectedFullPath, IPath actualFullPath) {
		isDeltaValid = false;

		message.append("\tConflicting full paths\n");
		message.append("\t\tExpected full path: ");
		message.append(expectedFullPath);
		message.append("\n");
		message.append("\t\tActual full path: ");
		message.append(actualFullPath);
		message.append("\n");
	}
	private void recordConflictingMovedFromPaths(IPath expectedMovedFromPath, IPath actualMovedFromPath) {
		isDeltaValid = false;

		message.append("\tConflicting moved from paths\n");
		message.append("\t\tExpected moved from path: ");
		message.append(expectedMovedFromPath);
		message.append("\n");
		message.append("\t\tActual moved from path: ");
		message.append(actualMovedFromPath);
		message.append("\n");
	}
	private void recordConflictingMovedToPaths(IPath expectedMovedToPath, IPath actualMovedToPath) {
		isDeltaValid = false;

		message.append("\tConflicting moved to paths\n");

		message.append("\t\tExpected moved to path: ");
		message.append(expectedMovedToPath);
		message.append("\n");

		message.append("\t\tActual moved to path: ");
		message.append(actualMovedToPath);
		message.append("\n");
	}
	private void recordConflictingProjectRelativePaths(IPath expectedProjectRelativePath, IPath actualProjectRelativePath) {
		isDeltaValid = false;

		message.append("\tConflicting project relative paths\n");
		message.append("\t\tExpected project relative path: ");
		message.append(expectedProjectRelativePath);
		message.append("\n");
		message.append("\t\tActual project relative path: ");
		message.append(actualProjectRelativePath);
		message.append("\n");
	}
	private void recordDuplicateChild(IPath path, int formerChildKind, int latterChildKind, int expectedKind) {
		isDeltaValid = false;

		message.append("\tDuplicate child: ");
		message.append(path);
		message.append("\n");
		message.append("\t\tProduced by IResourceDelta.get");

		switch (expectedKind) {
			case IResourceDelta.ADDED :
				message.append("Added");
				break;
			case IResourceDelta.CHANGED :
				message.append("Changed");
				break;
			case IResourceDelta.REMOVED :
				message.append("Removed");
				break;
		}

		message.append("Children()\n");
		message.append("\t\tFormer child's status: ");
		message.append(convertKind(formerChildKind));
		message.append("\n");
		message.append("\t\tLatter child's status: ");
		message.append(convertKind(latterChildKind));
		message.append("\n");
	}
	private void recordIllegalChild(IPath path, int expectedKind, int actualKind) {
		isDeltaValid = false;

		message.append("\tIllegal child: ");
		message.append(path);
		message.append("\n");
		message.append("\t\tProduced by IResourceDelta.get");

		switch (expectedKind) {
			case IResourceDelta.ADDED :
				message.append("Added");
				break;
			case IResourceDelta.CHANGED :
				message.append("Changed");
				break;
			case IResourceDelta.REMOVED :
				message.append("Removed");
				break;
		}

		message.append("Children()\n");

		message.append("\t\tIlleagal child's status: ");
		message.append(convertKind(actualKind));
		message.append("\n");
	}
	private void recordMissingActualChange(int kind, int changeFlags) {
		isDeltaValid = false;

		message.append("\tMissing actual change\n");
		message.append("\t\tExpected kind: <");
		message.append(convertKind(kind));
		message.append(">\n");
		message.append("\t\tExpected change flags: <");
		message.append(convertChangeFlags(changeFlags));
		message.append(">\n");
	}
	private void recordMissingChild(IPath path, int kind, boolean isMissingFromAffectedChildren) {
		isDeltaValid = false;

		message.append("\tMissing child: ");
		message.append(path);
		message.append("\n");
		message.append("\t\tfrom IResourceDelta.getAffectedChildren(");

		if (!isMissingFromAffectedChildren) {
			switch (kind) {
				case IResourceDelta.ADDED :
					message.append("ADDED");
					break;
				case IResourceDelta.CHANGED :
					message.append("CHANGED");
					break;
				case IResourceDelta.REMOVED :
					message.append("REMOVED");
					break;
				default :
					message.append(kind);
			}
		}
		message.append(")\n");
	}
	private void recordMissingExpectedChange(int kind, int changeFlags) {
		isDeltaValid = false;

		message.append("\tMissing expected change\n");
		message.append("\t\tActual kind: <");
		message.append(convertKind(kind));
		message.append(">\n");
		message.append("\t\tActual change flags: <");
		message.append(convertChangeFlags(changeFlags));
		message.append(">\n");
	}
	/**
	 * Resets the listener to its initial state.
	 */
	public void reset() {
		expectedChanges.clear();
		isDeltaValid = true;
		message.setLength(0);
		setState(RECEIVING_INPUTS);
	}
	private void resetIfNecessary() {
		if (state == DELTA_VERIFIED) {
			reset();
		}
	}
	/**
	 * Part of the <code>IResourceChangedListener</code> interface.
	 * @see IResourceChangedListener
	 */
	public void resourceChanged(IResourceChangeEvent e) {
		verifyDelta(e.getDelta());
	}
	/**
	 * Compares the given delta with the expected changes.  Recursively
	 * compares child deltas.
	 */
	public void verifyDelta(IResourceDelta delta) {
		internalVerifyDelta(delta);
		setState(DELTA_VERIFIED);
	}
	private synchronized void setState(int state) {
		this.state = state;
		//wake up any threads waiting for a delta
		notifyAll();
	}
		
	/**
	 * Waits until a delta is received.
	 */
	public void waitForDelta() {
	}
}