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
import java.util.*;
import org.eclipse.core.internal.localstore.BucketIndex3.BatchVisitor;
import org.eclipse.core.internal.localstore.BucketIndex3.Visitor;
import org.eclipse.core.internal.resources.*;
import org.eclipse.core.internal.utils.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

public class NewHistoryStore3 implements IHistoryStore {
	private static final String BLOB_STORE = ".blobs"; //$NON-NLS-1$
	private static final String INDEX_STORE = ".index"; //$NON-NLS-1$
	private final static int SEGMENT_LENGTH = 2;
	private final static long SEGMENT_QUOTA = (long) Math.pow(2, 4 * SEGMENT_LENGTH); // 1 char = 2 ^ 4 = 0x10
	private File baseLocation;
	private BlobStore blobStore;
	private BucketIndex3 currentBucket;
	private File indexLocation;
	private Workspace workspace;

	public NewHistoryStore3(Workspace workspace, IPath location, int limit) {
		this.workspace = workspace;
		this.baseLocation = location.toFile();
		IPath blobStoreLocation = location.append(BLOB_STORE);
		blobStoreLocation.toFile().mkdirs();
		this.blobStore = new BlobStore(blobStoreLocation, limit);
		this.indexLocation = location.append(INDEX_STORE).toFile();
		this.currentBucket = new BucketIndex3(indexLocation);
	}

	public void accept(Visitor visitor, IPath root, int depth) throws CoreException {
		accept(visitor, root, depth, false);
	}

	/**
	 * From a starting point in the tree, visit all nodes under it. 
	 * @param visitor
	 * @param root
	 * @param depth
	 */
	public void accept(Visitor visitor, IPath root, int depth, boolean sorted) throws CoreException {
		// we only do anything for the root if depth == infinite
		if (root.isRoot()) {
			if (depth != IResource.DEPTH_INFINITE)
				// root with depth < infinite... nothing to be done
				return;
			// visit all projects DEPTH_INFINITE
			File[] projects = indexLocation.listFiles();
			if (projects == null || projects.length == 0)
				return;
			for (int i = 0; i < projects.length; i++)
				if (projects[i].isDirectory())
					if (!internalAccept(visitor, root.append(projects[i].getName()), projects[i], IResource.DEPTH_INFINITE, sorted))
						break;
			// done
			return;
		}
		// handles the case the starting point is a file path
		if (root.segmentCount() > 1) {
			currentBucket.load(locationFor(root.removeLastSegments(1)));
			if (currentBucket.accept(visitor, root, true, sorted) != BucketIndex3.Visitor.CONTINUE || depth == IResource.DEPTH_ZERO)
				return;
		}
		internalAccept(visitor, root, locationFor(root), depth, sorted);
	}

	/**
	 * @see IHistoryStore#addState(IPath, File, long, boolean)
	 */
	public IFileState addState(IPath key, java.io.File localFile, long lastModified, boolean moveContents) {
		if (Policy.DEBUG_HISTORY)
			System.out.println("History: Adding state for key: " + key + ", file: " + localFile + ", timestamp: " + lastModified + ", size: " + localFile.length()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if (!isValid(localFile))
			return null;
		UniversalUniqueIdentifier uuid = null;
		try {
			uuid = blobStore.addBlob(localFile, moveContents);
			File bucketDir = locationFor(key.removeLastSegments(1));
			currentBucket.load(bucketDir);
			currentBucket.addBlob(key, uuid);
			currentBucket.save();
		} catch (CoreException e) {
			ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
		}
		return new FileState(this, key, lastModified, uuid);
	}

	public Set allFiles(IPath root, int depth, IProgressMonitor monitor) {
		final Set allFiles = new HashSet();
		try {
			accept(new BatchVisitor() {
				public int visit(IPath path, byte[][] uuids) {
					allFiles.add(path);
					return CONTINUE;
				}
			}, root, depth);
		} catch (CoreException e) {
			ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
		}
		return allFiles;
	}

	private void clean(IPath root) {
		try {
			IWorkspaceDescription description = workspace.internalGetDescription();
			final long minimumTimestamp = System.currentTimeMillis() - description.getFileStateLongevity();
			final int max = description.getMaxFileStates();
			final BlobStore tmpBlobStore = this.blobStore;
			accept(new Visitor() {
				IPath lastVisited;
				int statesCounter;

				public int visit(IPath path, byte[] uuid) {
					if (!path.equals(lastVisited)) {
						lastVisited = path;
						statesCounter = 0;
					}
					UniversalUniqueIdentifier uuidObject = new UniversalUniqueIdentifier(uuid);
					if (++statesCounter <= max) {
						File blobFile = tmpBlobStore.fileFor(uuidObject);
						if (blobFile.lastModified() >= minimumTimestamp)
							return CONTINUE;
					}
					return DELETE;
				}
			}, root, IResource.DEPTH_INFINITE, true);
		} catch (Exception e) {
			String message = Policy.bind("history.problemsCleaning"); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.FAILED_DELETE_LOCAL, null, message, e);
			ResourcesPlugin.getPlugin().getLog().log(status);
		}
	}

	public void clean(IProgressMonitor monitor) {
		clean(Path.ROOT);
	}

	public void copyHistory(IResource sourceResource, IResource destinationResource) {
		// return early if either of the paths are null or if the source and
		// destination are the same.
		if (sourceResource == null || destinationResource == null) {
			String message = Policy.bind("history.copyToNull"); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.INTERNAL_ERROR, null, message, null);
			ResourcesPlugin.getPlugin().getLog().log(status);
			return;
		}
		if (sourceResource.equals(destinationResource)) {
			String message = Policy.bind("history.copyToSelf"); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.INTERNAL_ERROR, sourceResource.getFullPath(), message, null);
			ResourcesPlugin.getPlugin().getLog().log(status);
			return;
		}

		final IPath source = sourceResource.getFullPath();
		final IPath destination = destinationResource.getFullPath();
		Assert.isLegal(source.segmentCount() > 0);
		Assert.isLegal(destination.segmentCount() > 0);
		Assert.isLegal(source.segmentCount() > 1 || destination.segmentCount() == 1);

		boolean file = sourceResource.getType() == IResource.FILE;

		final IPath baseSourceLocation = Path.fromOSString(locationFor(source.removeLastSegments(file ? 1 : 0)).toString());
		final IPath baseDestinationLocation = Path.fromOSString(locationFor(destination.removeLastSegments(file ? 1 : 0)).toString());

		try {

			// special case: source and origin are the same bucket (renaming a file/copying a file/folder to the same directory) 
			if (baseSourceLocation.equals(baseDestinationLocation)) {
				currentBucket.load(baseSourceLocation.toFile());
				byte[][] uuids = currentBucket.getUUIDs(source);
				for (int i = 0; i < uuids.length; i++)
					currentBucket.addBlob(destination, uuids[i]);
				currentBucket.save();
				clean(destinationResource.getFullPath());
				return;
			}
			final BucketIndex3 sourceBucket = currentBucket;
			final BucketIndex3 destinationBucket = new BucketIndex3(indexLocation);

			final IPath[] sourceDir = new IPath[1];
			final IPath[] destinationDir = new IPath[1];

			accept(new BatchVisitor() {
				public void newBucket(BucketIndex3 newBucket) {
					// figure out where we want to copy the states for this path to by:
					// destinationBucket = baseDestinationLocation + blob - filename - baseSourceLocation
					sourceDir[0] = Path.fromOSString(sourceBucket.getLocation().toString());
					destinationDir[0] = baseDestinationLocation.append(sourceDir[0].removeFirstSegments(baseSourceLocation.segmentCount()));
					try {
						destinationBucket.load(destinationDir[0].toFile());
					} catch (CoreException e) {
						ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
					}
				}

				public int visit(IPath path, byte[][] uuids) {
					IPath destinationPath = destination.append(path.removeFirstSegments(source.segmentCount()));
					destinationBucket.addBlobs(destinationPath, uuids);
					return CONTINUE;
				}
			}, source, IResource.DEPTH_INFINITE);
			// the last one may not have saved the bucket
			destinationBucket.save();
		} catch (CoreException e) {
			ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
		}
		clean(destinationResource.getFullPath());
	}

	public boolean exists(IFileState target) {
		return blobStore.fileFor(((FileState) target).getUUID()).exists();
	}

	public InputStream getContents(IFileState target) throws CoreException {
		if (!target.exists()) {
			String message = Policy.bind("history.notValid"); //$NON-NLS-1$
			throw new ResourceException(IResourceStatus.FAILED_READ_LOCAL, target.getFullPath(), message, null);
		}
		return blobStore.getBlob(((FileState) target).getUUID());
	}

	public File getFileFor(IFileState state) {
		return blobStore.fileFor(((FileState) state).getUUID());
	}

	public IFileState[] getStates(IPath filePath, IProgressMonitor monitor) {
		File bucketDir = locationFor(filePath.removeLastSegments(1));
		try {
			currentBucket.load(bucketDir);
			final List states = new ArrayList();
			final BlobStore tmpBlobStore = blobStore;
			accept(new BatchVisitor() {
				public int visit(IPath path, byte[][] uuids) {
					for (int i = 0; i < uuids.length; i++) {
						UniversalUniqueIdentifier blobUUID = new UniversalUniqueIdentifier(uuids[i]);
						File blobFile = tmpBlobStore.fileFor(blobUUID);
						states.add(new FileState(NewHistoryStore3.this, path, blobFile.lastModified(), blobUUID));
					}
					return CONTINUE;
				}
			}, filePath, IResource.DEPTH_ZERO, true);
			IFileState[] result = (IFileState[]) states.toArray(new IFileState[states.size()]);
			Arrays.sort(result, new Comparator() {
				public int compare(Object o1, Object o2) {
					FileState fs1 = (FileState) o1;
					FileState fs2 = (FileState) o2;
					if (fs1.getModificationTime() == fs1.getModificationTime()) {
						return -UniversalUniqueIdentifier.compareTime(fs1.getUUID(), fs2.getUUID());
					}
					return (fs1.getModificationTime() < fs2.getModificationTime()) ? 1 : -1;
				}
			});
			return result;
		} catch (CoreException ce) {
			ResourcesPlugin.getPlugin().getLog().log(ce.getStatus());
			return new IFileState[0];
		}
	}

	/**
	 * 
	 * @return whether to continue visiting other branches 
	 */
	private boolean internalAccept(Visitor visitor, IPath root, File bucketDir, int depth, boolean sorted) throws CoreException {
		currentBucket.load(bucketDir);
		int outcome = currentBucket.accept(visitor, root, depth == IResource.DEPTH_ZERO, sorted);
		if (outcome != Visitor.CONTINUE)
			return outcome == Visitor.RETURN;
		// nothing else to be done
		if (depth != IResource.DEPTH_INFINITE)
			return true;
		File[] subDirs = bucketDir.listFiles();
		if (subDirs == null)
			return true;
		for (int i = 0; i < subDirs.length; i++)
			if (subDirs[i].isDirectory())
				if (!internalAccept(visitor, root, subDirs[i], IResource.DEPTH_INFINITE, sorted))
					return false;
		return true;
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

	/**
	 * Returns the index location corresponding to the given path. 
	 */
	private File locationFor(IPath resourcePath) {
		int segmentCount = resourcePath.segmentCount();
		// the root
		if (segmentCount == 0)
			return indexLocation;
		// a project
		if (segmentCount == 1)
			return new File(indexLocation, resourcePath.segment(0));
		// a folder
		IPath location = new Path(resourcePath.segment(0));
		for (int i = 1; i < segmentCount; i++)
			// translate all segments except the first one (project name)
			location = location.append(translateSegment(resourcePath.segment(i)));
		return new File(indexLocation, location.toOSString());
	}

	public void remove(IPath root, IProgressMonitor monitor) {
		try {
			accept(new BatchVisitor() {
				public int visit(IPath path, byte[][] uuid) {
					return DELETE;
				}
			}, root, IResource.DEPTH_INFINITE);
		} catch (CoreException ce) {
			ResourcesPlugin.getPlugin().getLog().log(ce.getStatus());
		}
	}

	public void shutdown(IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub

	}

	public void startup(IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub

	}

	private String translateSegment(String segment) {
		// TODO we have to provide a string hashcode ourselves 
		// because it changes from VM to VM
		return Long.toHexString(Math.abs(segment.hashCode()) % SEGMENT_QUOTA);
	}
	
	/**
	 * @see IHistoryStore#removeGarbage()
	 */
	public void removeGarbage() {
		//TODO implement this
	}

}
