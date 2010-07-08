/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.resources;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.internal.utils.FileUtil;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceFactory;
import org.eclipse.core.runtime.CoreException;

/**
 * Default implementation of {@link IWorkspaceFactory}.
 */
public class WorkspaceFactory implements IWorkspaceFactory {
	/**
	 * Map<URI,IWorkspace> tracking known workspace handles for different locations.
	 */
	private Map workspaces = new HashMap();

	/*(non-Javadoc)
	 * @see org.eclipse.core.resources.IWorkspaceFactory#getWorkspace(java.net.URI)
	 */
	public synchronized IWorkspace constructWorkspace(URI location) throws CoreException {
		// use a canonical form
		location = FileUtil.canonicalURI(location);
		Workspace result = (Workspace) workspaces.get(location);
		if (result == null) {
			result = new Workspace(URIUtil.toPath(location));
			workspaces.put(location, result);
			if (!result.getMetaArea().hasSavedWorkspace())
				result.getMetaArea().createMetaArea();
		}
		return result;
	}
}
