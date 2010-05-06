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
package org.eclipse.core.resources;

import org.eclipse.core.runtime.CoreException;

import java.net.URI;

/**
 * A workspace factory is used to construct or obtain instances of {@link IWorkspace}.
 * @since 4.0
 */
public interface IWorkspaceFactory {
	/**
	 * The name of the IWorkspace OSGi service (value "org.eclipse.core.resources.IWorkspaceFactory").
	 */
	public static final String SERVICE_NAME = IWorkspaceFactory.class.getName();

	/**
	 * Returns a workspace corresponding to the given location. If no such
	 * workspace exists, a new one will be created. The returned workspace
	 * may already be open if it had been previously been constructed during
	 * this session.
	 * @param location The location of the workspace.
	 * @return The workspace at the given location
	 * @exception CoreException if the workspace structure could not be constructed.
	 * Reasons include:
	 * <ul>
	 * <li> A file exists at the given location in the local file system.
	 * <li> A directory could not be created at the given location in the
	 *      local file system.
	 * </ul>
	 */
	public IWorkspace constructWorkspace(URI location) throws CoreException;

}
