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
import org.eclipse.core.internal.localstore.AbstractBucketIndex.IVisitor;
import org.eclipse.core.internal.localstore.BucketIndex.Visitor;
import org.eclipse.core.runtime.*;

public class BucketTree {
	
	public static final int DEPTH_ZERO = 0;
	public static final int DEPTH_ONE = 1;
	public static final int DEPTH_INFINITE = Integer.MAX_VALUE;
	
	
	private final static int SEGMENT_LENGTH = 2;
	private final static long SEGMENT_QUOTA = (long) Math.pow(2, 4 * SEGMENT_LENGTH); // 1 char = 2 ^ 4 = 0x10	
	private AbstractBucketIndex current;

	private File rootLocation;

	public BucketTree(File rootLocation, AbstractBucketIndex bucket) {
		this.rootLocation = rootLocation;
		this.current = bucket;
	}

	/**
	 * From a starting point in the tree, visit all nodes under it. 
	 * @param visitor
	 * @param root
	 * @param depth
	 */
	public void accept(IVisitor visitor, IPath root, int depth) throws CoreException {
		internalAccept(visitor, root, locationFor(root), depth, 0);
	}

	/**
	 * @return whether to continue visiting other branches 
	 */
	private boolean internalAccept(IVisitor visitor, IPath root, File bucketDir, int depthRequested, int currentDepth) throws CoreException {
		current.load(bucketDir);
		int outcome = current.accept(visitor, root, depthRequested);
		if (outcome != Visitor.CONTINUE)
			return outcome == Visitor.RETURN;
		if (depthRequested == currentDepth)
			return true;
		File[] subDirs = bucketDir.listFiles();
		if (subDirs == null)
			return true;
		for (int i = 0; i < subDirs.length; i++)
			if (subDirs[i].isDirectory())
				if (!internalAccept(visitor, root, subDirs[i], depthRequested, currentDepth + 1))
					return false;
		return true;
	}

	public File locationFor(IPath resourcePath) {
		int segmentCount = resourcePath.segmentCount();
		// the root
		if (segmentCount == 0)
			return rootLocation;
		// a project
		if (segmentCount == 1)
			return new File(rootLocation, resourcePath.segment(0));
		// a folder or file
		IPath location = new Path(resourcePath.segment(0));
		// the last segment is ignored
		for (int i = 1; i < segmentCount - 1; i++)
			// translate all segments except the first one (project name)
			location = location.append(translateSegment(resourcePath.segment(i)));
		return new File(rootLocation, location.toOSString());
	}

	private String translateSegment(String segment) {
		// String.hashCode algorithm is API
		return Long.toHexString(Math.abs(segment.hashCode()) % SEGMENT_QUOTA);
	}

	public AbstractBucketIndex getCurrent() {
		return current;
	}

	public void loadBucketFor(IPath path) throws CoreException {
		current.load(locationFor(path));
	}

	public void close() throws CoreException {
		current.save();
	}
}