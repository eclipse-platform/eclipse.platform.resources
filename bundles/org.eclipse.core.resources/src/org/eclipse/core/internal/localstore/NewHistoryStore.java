/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.localstore;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.core.internal.resources.*;
import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.internal.utils.UniversalUniqueIdentifier;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

public class NewHistoryStore implements IHistoryStore {

	private Workspace workspace;
	private NewBlobStore2 blobStore;

	public NewHistoryStore(Workspace workspace, IPath location, int limit) {
		this.workspace = workspace;
		this.blobStore = new NewBlobStore2(location, limit);
	}

	public IFileState addState(IPath key, File localFile, long lastModified, boolean moveContents) {
		if (!isValid(localFile))
			return null;
		//add the state to the blob store
		UniversalUniqueIdentifier uuid = null;
		try {
			uuid = blobStore.addBlob(key, localFile, moveContents);
		} catch (CoreException e) {
			ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
		}
		// remember uuid to facilitate retrieving the state later
		return new FileState(this, key, lastModified, uuid);
	}

	public Set allFiles(IPath root, int depth, IProgressMonitor monitor) {
		final Set allFiles = new HashSet();
		class PathCollector implements IBlobVisitor {
			public boolean visit(IPath path, java.io.File blob) {
				allFiles.add(path);
				return true;
			}
		}
		try {
			blobStore.accept(root, new PathCollector(), depth, false);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return allFiles;
	}

	public void clean(IProgressMonitor monitor) {
		clean(workspace.getRoot(), monitor);
	}
	
	/**
	 * @see IHistoryStore#clean(IProgressMonitor)
	 */
	public void clean(IResource resource, IProgressMonitor monitor) {
		try {
			IWorkspaceDescription description = workspace.internalGetDescription();
			final long minimumTimestamp = System.currentTimeMillis() - description.getFileStateLongevity();
			final int max = description.getMaxFileStates();

			blobStore.accept(resource.getFullPath(), new IBlobVisitor() {
				IPath lastVisited;
				int statesCounter;

				public boolean visit(IPath path, File blob) {
					if (!path.equals(lastVisited)) {
						lastVisited = path;
						statesCounter = 0;
					}
					if (++statesCounter > max || blob.lastModified() < minimumTimestamp)
						blobStore.deleteBlob(path, blob);
					return true;
				}
			}, IResource.DEPTH_INFINITE, true);
		} catch (Exception e) {
			String message = Policy.bind("history.problemsCleaning"); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.FAILED_DELETE_LOCAL, null, message, e);
			ResourcesPlugin.getPlugin().getLog().log(status);
		}
	}

	public void copyHistory(IResource source, IResource destination) {
		// return early if either of the paths are null or if the source and
		// destination are the same.
		if (source == null || destination == null) {
			String message = Policy.bind("history.copyToNull"); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.INTERNAL_ERROR, null, message, null);
			ResourcesPlugin.getPlugin().getLog().log(status);
			return;
		}
		if (source.equals(destination)) {
			String message = Policy.bind("history.copyToSelf"); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.INTERNAL_ERROR, source.getFullPath(), message, null);
			ResourcesPlugin.getPlugin().getLog().log(status);
			return;
		}
		try {
			blobStore.copy(source.getFullPath(), destination.getFullPath(), source.getType() == IResource.FILE);
			clean(destination, null);
		} catch (CoreException e) {
			//TODO 
			e.printStackTrace();
		}
	}

	public boolean exists(IFileState target) {
		File blobFile = blobStore.findFileFor(target.getFullPath(), ((FileState) target).getUUID());
		return blobFile != null && blobFile.isFile();
	}

	public InputStream getContents(IFileState target) throws CoreException {
		if (target.exists()) {
			InputStream blobContents = blobStore.getBlobContents(target.getFullPath(), ((FileState) target).getUUID());
			if (blobContents != null)
				return blobContents;
		}
		String message = Policy.bind("history.notValid"); //$NON-NLS-1$
		throw new ResourceException(IResourceStatus.FAILED_READ_LOCAL, target.getFullPath(), message, null);
	}

	public IFileState[] getStates(IPath path, IProgressMonitor monitor) {
		File[] blobFiles = null;
		try {
			blobFiles = blobStore.findFilesFor(path, true);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		if (blobFiles == null || blobFiles.length == 0)
			return ICoreConstants.EMPTY_FILE_STATES;
		IFileState[] states = new IFileState[blobFiles.length];
		for (int i = 0; i < states.length; i++)
			states[i] = new FileState(NewHistoryStore.this, path, blobFiles[i].lastModified(), blobStore.getUUID(blobFiles[i]));
		return states;
	}

	public File getFileFor(IFileState state) {
		return blobStore.findFileFor(state.getFullPath(), ((FileState) state).getUUID());
	}

	
	public void remove(IPath path, IProgressMonitor monitor) {
		try {
			blobStore.deleteAll(path);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void shutdown(IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub
	}

	public void startup(IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub
	}

	/**
	 * Return a boolean value indicating whether or not the given file
	 * should be added to the history store based on the current history
	 * store policies.
	 * 
	 * @param localFile the file to check
	 * @return <code>true</code> if this file should be added to the history
	 * 	store and <code>false</code> otherwise
	 */
	private boolean isValid(java.io.File localFile) {
		WorkspaceDescription description = workspace.internalGetDescription();
		boolean result = localFile.length() <= description.getMaxFileStateSize();
		if (Policy.DEBUG_HISTORY && !result)
			System.out.println("History: Ignoring file (too large). File: " + localFile.getAbsolutePath() + //$NON-NLS-1$
					", size: " + localFile.length() + //$NON-NLS-1$
					", max: " + description.getMaxFileStateSize()); //$NON-NLS-1$
		return result;
	}
	
	public void removeGarbage() {
		// not implemented
	}

}
