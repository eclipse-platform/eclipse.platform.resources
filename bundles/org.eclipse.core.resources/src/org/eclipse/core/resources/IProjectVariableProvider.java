/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Serge Beauchamp (Freescale Semiconductor) - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.resources;

/**
 * An interface that variable providers should implement in order
 * to extends the default path variable list used to resolve relative
 * locations of linked resources.
 */
public interface IProjectVariableProvider {

	/**
	 * Returns a variable value
	 * 
	 * @param variable
	 *            The current variable name.
	 * @param project
	 *            The project that the variable is being resolved for.
	 * @return the variable value.
	 */
	public String getValue(String variable, IProject project);

	/**
	 * If the variable supports extensions (specified as
	 * "${VARNAME-EXTENSIONNAME}"), this method can return the list of possible
	 * extensions, or null if none are supported.
	 * 
	 * @param variable
	 *            The current variable name.
	 * @param project
	 *            The project that the variable is being resolved for.
	 * @return the possible variable extensions or null if none are supported.
	 */
	public Object[] getExtensions(String variable, IProject project);
}
