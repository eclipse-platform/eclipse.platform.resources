/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Serge Beauchamp(Freeescale Semiconductor) - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.resources;

import org.eclipse.core.filesystem.IFileInfo;

/**
 * Represents a resource filter type instance that is able to 
 * calculate whether a file system object matches a criteria or not.
 * 
 */
public interface IFilterType  {

	/**
	 * Return if this filter matches with the fileInfo provided.
	 * 
	 * @param fileInfo the object to test
	 * @return true or false, whether this filter matches the fileInfo and arguments.
	 */
	public boolean matches(IFileInfo fileInfo);
}
