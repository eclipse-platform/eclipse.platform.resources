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

import java.io.*;
import java.io.File;
import java.util.*;
import org.eclipse.core.internal.resources.IBlobVisitor;
import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.internal.utils.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

public class NewBlobStore2 {

	private BucketIndex bucket = new BucketIndex();

	private final static String BLOB_EXTENSION = ".b"; //$NON-NLS-1$

	private final static FileFilter BUCKET_FILTER = new FileFilter() {
		public boolean accept(File pathName) {
			return pathName.getName().indexOf('.') == -1;
		}
	};

	private final static byte[] randomArray = {-43, -25, 37, 85, -45, 29, -95, -81, -69, 3, -109, -10, -86, 30, -54, -73, -14, 47, -2, -67, 25, -8, -63, 2, 119, -123, 125, 12, 76, -43, -37, 79, 69, -123, -54, 80, -106, -66, -99, -66, 80, -66, -37, -106, -87, 117, 95, 10, 77, -42, -23, 70, 5, -68, 44, 91, -91, -107, -79, 93, 17, 112, 4, 41, -26, -108, -68, 107, -43, 31, 52, 60, 111, -10, -30, 121, -127, -59, -112, -8, 92, -123, 96, 116, 104, 67, 74, -112, -71, -115, 96, 34, -74, 90, 36, -39, 28, -51, 107, 52, -55, 14, 8, 1, 27, -40, 60, 35, -5, -62, 7, -100, 32, 5, -111, 29, 96, 61, 110, -111, 50, 56, -21, -17, -86, -118, 17, -45, 56, 98, 101, 126, 27, 57, -45, -112, -50, -49, -77, 111, -96, 50, -13, 69, 106, 118, -101, -97, 28, 57, 11, -81, 43, -83, 96, -75, 99, -87, -85, -100, -10, -13,
			30, -58, -5, 81, 77, 92, -96, -21, -41, -69, 23, 71, 58, -9, 127, 56, 118, -124, 79, -68, 42, -68, -98, 121, -1, 65, -102, 118, -84, -39, 4, 47, 105, -52, -121, 27, 43, 90, 9, 31, 59, 115, -63, 28, 55, 101, 9, 117, -45, 112, 61, 55, 23, -21, 51, 104, 123, -118, 76, -108, 115, 119, 81, 54, 39, 46, -107, -65, 79, 16, -34, 69, -37, -120, -108, -75, 77, -6, 101, -33, -116, -62, -115, 44, -61, -39, 31, -33, -49, -107, -11, 115, -13, -73,};
	private static final Comparator UUID_COMPARATOR = new Comparator() {
		public int compare(Object o1, Object o2) {
			return -UniversalUniqueIdentifier.compareTime(((File) o1).getName().substring(0, 32), ((File) o2).getName().substring(0, 32));
		}
	};

	private File baseLocation;
	private int limit;
	private FileSystemStore localStore;

	private static void appendByteString(StringBuffer buffer, byte value) {
		String hexString;
		if (value < 0)
			hexString = Integer.toHexString(256 + value);
		else
			hexString = Integer.toHexString(value);
		if (hexString.length() == 1)
			buffer.append("0"); //$NON-NLS-1$
		buffer.append(hexString);
	}

	private static String fileNameFor(IPath original, String existingFileName, String suffix) {
		StringBuffer buffer = new StringBuffer(100);
		// borrows the UUID
		buffer.append(existingFileName.substring(0, 32));
		buffer.append('.');
		buffer.append(original.lastSegment());
		buffer.append(suffix);
		return buffer.toString();
	}

	private static String fileNameFor(IPath original, UniversalUniqueIdentifier uuid, String suffix) {
		byte[] bytes = uuid.toBytes();
		StringBuffer buffer = new StringBuffer(100);
		for (int i = 0; i < bytes.length; i++)
			appendByteString(buffer, bytes[i]);
		buffer.append('.');
		buffer.append(original.lastSegment());
		buffer.append(suffix);
		return buffer.toString();
	}

	public static UniversalUniqueIdentifier getUUID(File blob) {
		return new UniversalUniqueIdentifier(blob.getName().substring(0, 32));
	}

	public NewBlobStore2(IPath location, int limit) {
		this.limit = limit;
		this.baseLocation = location.toFile();
		localStore = new FileSystemStore();
	}

	private boolean accept(IPath containerPath, File root, IBlobVisitor visitor, int depth, boolean sorted) throws CoreException {
		if (depth == IResource.DEPTH_ZERO) {
			File[] blobs = findFilesFor(containerPath, sorted);
			for (int i = 0; i < blobs.length; i++)
				if (!visitor.visit(containerPath, blobs[i]))
					return false;
			return true;
		}

		int newDepth = depth == IResource.DEPTH_INFINITE ? IResource.DEPTH_INFINITE : IResource.DEPTH_ZERO;

		if (!visitBlobs(containerPath, root, visitor, sorted))
			return false;
		File[] subDirs = root.listFiles(BUCKET_FILTER);
		if (subDirs == null)
			return true;
		for (int i = 0; i < subDirs.length; i++)
			if (!accept(containerPath, subDirs[i], visitor, newDepth, sorted))
				return false;
		return true;
	}

	public void accept(IPath containerPath, IBlobVisitor visitor, int depth, boolean sorted) throws CoreException {
		if (containerPath.isRoot()) {
			if (depth == IResource.DEPTH_ZERO)
				return;
			int newDepth = depth == IResource.DEPTH_ONE ? IResource.DEPTH_ZERO : IResource.DEPTH_INFINITE;
			File[] projects = baseLocation.listFiles();
			if (projects == null)
				return;
			
			for (int i = 0; i < projects.length; i++)
				if (projects[i].isDirectory())
					if (!accept(containerPath.append(projects[i].getName()), projects[i], visitor, newDepth, sorted))
						break;
		} else {
			// special case: the container may have been a file before		
			File[] blobs = findFilesFor(containerPath, sorted);
			for (int i = 0; i < blobs.length; i++)
				if (!visitor.visit(containerPath, blobs[i]))
					return;
			// now visit all states under the container
			File dir = locationFor(containerPath);
			if (dir != null && dir.isDirectory())
				accept(containerPath, dir, visitor, depth, sorted);
		}
	}

	public UniversalUniqueIdentifier addBlob(IPath path, File target, boolean moveContents) throws CoreException {
		File dir = locationFor(path.removeLastSegments(1));
		if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
			String message = Policy.bind("localstore.couldNotCreateFolder", dir.getAbsolutePath()); //$NON-NLS-1$
			throw new ResourceException(IResourceStatus.FAILED_WRITE_LOCAL, new Path(dir.getAbsolutePath()), message, null);
		}
		UniversalUniqueIdentifier uuid = new UniversalUniqueIdentifier();
		File destination = fileFor(dir, path, uuid, BLOB_EXTENSION);
		if (moveContents)
			localStore.move(target, destination, true, null);
		else
			localStore.copy(target, destination, IResource.DEPTH_ZERO, null);
		bucket.load(destination.getParentFile());
		bucket.addBlob(path, uuid);
		bucket.save();
		return uuid;
	}

	public UniversalUniqueIdentifier addBlob(IPath path, InputStream content) throws CoreException {
		File dir = locationFor(path.removeLastSegments(1));
		if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
			String message = Policy.bind("localstore.couldNotCreateFolder", dir.getAbsolutePath()); //$NON-NLS-1$
			throw new ResourceException(IResourceStatus.FAILED_WRITE_LOCAL, new Path(dir.getAbsolutePath()), message, null);
		}
		UniversalUniqueIdentifier uuid = new UniversalUniqueIdentifier();
		File destination = fileFor(dir, path, uuid, BLOB_EXTENSION);
		localStore.write(destination, content, false, null);
		bucket.load(destination.getParentFile());
		bucket.addBlob(path, uuid);
		bucket.save();
		return uuid;
	}

	/**
	 * Copies all blobs under the source path to the destination path.
	 * 
	 */
	public void copy(final IPath source, final IPath destination, boolean file) throws CoreException {
		Assert.isLegal(source.segmentCount() > 0);
		Assert.isLegal(destination.segmentCount() > 0);
		Assert.isLegal(source.segmentCount() > 1 || destination.segmentCount() == 1);

		final IPath baseSourceLocation = Path.fromOSString(locationFor(source.removeLastSegments(file ? 1 : 0)).toString());
		final IPath baseDestinationLocation = Path.fromOSString(locationFor(destination.removeLastSegments(file ? 1 : 0)).toString());

		bucket.load(baseSourceLocation.toFile());		
		final BucketIndex destinationBucket = baseSourceLocation.equals(baseDestinationLocation) ? bucket : new BucketIndex();
		// need to play with relative paths 
		accept(source, new IBlobVisitor() {
			public boolean visit(IPath sourcePath, File blob) {
				// need to figure out where we want to copy this blob to by:
				// destinationBucket = baseDestinationLocation + blob - filename - baseSourceLocation
				try {
					IPath sourceBucket = Path.fromOSString(blob.getParent());
					IPath destinationDir = baseDestinationLocation.append(sourceBucket.removeFirstSegments(baseSourceLocation.segmentCount()));
					destinationBucket.load(destinationDir.toFile());
					IPath destinationPath = destination.append(sourcePath.removeFirstSegments(source.segmentCount()));
					File destinationFile = new File(destinationDir.toFile(), fileNameFor(destinationPath, blob.getName(), BLOB_EXTENSION));
					localStore.copy(blob, destinationFile, IResource.DEPTH_ZERO, null);
					destinationBucket.addBlob(destinationPath, getUUID(blob));					
				} catch (CoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return true;
			}
		}, IResource.DEPTH_INFINITE, false);
		// the last one may not have saved the bucket
		destinationBucket.save();

	}

	public void deleteAll() {
		deleteAll(baseLocation);
	}

	private void deleteAll(File root) {
		deleteAll(root, false);
	}

	private boolean deleteAll(File root, boolean emptyDirsOnly) {
		boolean deletedAll = true;
		if (root.isDirectory()) {
			File[] dirs = root.listFiles();
			if (dirs != null)
				for (int i = 0; i < dirs.length; i++)
					deletedAll &= deleteAll(dirs[i], emptyDirsOnly);
			if (!emptyDirsOnly || deletedAll)
				root.delete();
		} else
			root.delete();
		return deletedAll;
	}

	public void deleteAll(IPath path) throws CoreException {
		bucket.clear();
		File dir = locationFor(path);
		//root/project case - just deleted the whole tree
		if (path.segmentCount() <= 1) {
			if (dir != null)
				deleteAll(dir);
			return;
		}
		//folder/file case - since there can be collisions, we have to be careful
		accept(path, new IBlobVisitor() {
			public boolean visit(IPath pathToVisit, File blob) {
				bucket.remove(pathToVisit, getUUID(blob));
				return true;
			}
		}, IResource.DEPTH_INFINITE, false);
		bucket.save();
		deleteAll(dir, true);
	}

	public File fileFor(File bucket, IPath path, UniversalUniqueIdentifier uuid, String suffix) {
		return new File(bucket, fileNameFor(path, uuid, suffix));
	}

	public File findFileFor(IPath path, UniversalUniqueIdentifier uuid) {
		File dir = locationFor(path.removeLastSegments(1));
		if (dir == null || !dir.isDirectory())
			return null;
		File destination = fileFor(dir, path, uuid, BLOB_EXTENSION);
		return destination.isFile() ? destination : null;
	}

	/**
	 * Returns all blobs corresponding to the given file path.
	 */
	public File[] findFilesFor(IPath path, boolean sorted) throws CoreException {
		File dir = locationFor(path.removeLastSegments(1));
		bucket.load(dir);
		String[] uuid = bucket.getUUIDs(path);
		if (uuid.length == 0)
			return new File[0];
		File[] blobs = new File[uuid.length];
		for (int i = 0; i < blobs.length; i++)
			blobs[i] = new File(dir, fileNameFor(path, new UniversalUniqueIdentifier(uuid[i]), BLOB_EXTENSION));
		if (sorted)
			Arrays.sort(blobs, UUID_COMPARATOR);
		return blobs;
	}

	public InputStream getBlobContents(IPath path, UniversalUniqueIdentifier uuid) throws CoreException {
		File blobFile = findFileFor(path, uuid);
		return blobFile == null ? null : localStore.read(blobFile);
	}

	/**
	 * Converts a byte array into a byte hash representation. It is used to
	 * get a directory name.
	 */
	protected byte hashBytes(byte[] bytes) {
		byte hash = 0;
		for (int i = 0; i < bytes.length; i++)
			hash ^= hash ^= randomArray[bytes[i] + 128]; // +128 makes sure the index is >0
		return hash;
	}

	/** 
	 * Returns the location for a dir blob bucket.
	 * @return a non-null FIle object
	 */
	public File locationFor(IPath resourcePath) {
		int segmentCount = resourcePath.segmentCount();
		// the root
		if (segmentCount == 0)
			return baseLocation;
		// a project
		if (segmentCount == 1)
			return new File(baseLocation, resourcePath.segment(0));
		// either a folder
		IPath location = new Path(resourcePath.segment(0));
		for (int i = 1; i < segmentCount; i++)
			// translate all segments except the first one
			location = location.append(translateSegment(resourcePath.segment(i)));
		return new File(baseLocation, location.toOSString());
	}

	private String translateSegment(String segment) {
		// TODO we have to provide a string hashcode ourselves 
		// because it changes from VM to VM
		return Integer.toHexString(Math.abs(segment.hashCode()) % limit);
	}

	private boolean visitBlobs(IPath containerPath, File blobsDir, IBlobVisitor visitor, boolean sorted) throws CoreException {
		bucket.load(blobsDir);
		BucketIndex.BlobInfo[] blobs = bucket.findChildren(containerPath);
		if (sorted)
			Arrays.sort(blobs);
		// visit all children blobs
		for (int i = 0; i < blobs.length; i++)
			if (!visitor.visit(blobs[i].filePath, new File(blobsDir, fileNameFor(blobs[i].filePath, new UniversalUniqueIdentifier(blobs[i].uuid), BLOB_EXTENSION))))
				return false;
		return true;
	}

	public void deleteBlob(IPath path, File blob) {
		try {
			bucket.load(blob.getParentFile());
			bucket.remove(path, getUUID(blob));
			bucket.save();			
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}