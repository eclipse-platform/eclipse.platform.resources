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

/**
 * Represents a resource filter type factory that instantiate 
 * filter types based on the matching criteria.
 * 
 */
public interface IFilterTypeFactory  {

	/**
	 * Initialize this filter instance with a project and arguments.
	 * The arguments can be null, depending on the filter type.
	 * 
	 * @param project the project from which this filter is called
	 * @param arguments the test arguments, or null
	 */
	public IFilterType instantiate(IProject project, String arguments);
}
