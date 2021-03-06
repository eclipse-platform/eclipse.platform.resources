/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.filesystem.memory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.filesystem.provider.FileStore;
import org.eclipse.core.runtime.*;

/**
 * In memory file system implementation used for testing.
 */
public class MemoryFileStore extends FileStore {
	private static final MemoryTree TREE = MemoryTree.TREE;

	private final IPath path;

	public MemoryFileStore(IPath path) {
		super();
		this.path = path.setDevice(null);
	}

	@Override
	public String[] childNames(int options, IProgressMonitor monitor) {
		final String[] names = TREE.childNames(path);
		return names == null ? EMPTY_STRING_ARRAY : names;
	}

	@Override
	public void delete(int options, IProgressMonitor monitor) throws CoreException {
		TREE.delete(path);
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) {
		return TREE.fetchInfo(path);
	}

	@Override
	public IFileStore getChild(String name) {
		return new MemoryFileStore(path.append(name));
	}

	@Override
	public String getName() {
		final String name = path.lastSegment();
		return name == null ? "" : name;
	}

	@Override
	public IFileStore getParent() {
		if (path.segmentCount() == 0)
			return null;
		return new MemoryFileStore(path.removeLastSegments(1));
	}

	@Override
	public IFileStore mkdir(int options, IProgressMonitor monitor) throws CoreException {
		TREE.mkdir(path, (options & EFS.SHALLOW) == 0);
		return this;
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		return TREE.openInputStream(path);
	}

	@Override
	public OutputStream openOutputStream(int options, IProgressMonitor monitor) throws CoreException {
		return TREE.openOutputStream(path, options);
	}

	@Override
	public void putInfo(IFileInfo info, int options, IProgressMonitor monitor) throws CoreException {
		TREE.putInfo(path, info, options);
	}

	@Override
	public URI toURI() {
		return MemoryFileSystem.toURI(path);
	}
}