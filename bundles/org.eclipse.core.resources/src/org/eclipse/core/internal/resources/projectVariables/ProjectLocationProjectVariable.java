/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.resources.projectVariables;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectVariableProvider;

/**
 * 
 */
public class ProjectLocationProjectVariable implements IProjectVariableProvider {

	public static String NAME = "PROJECT_LOC"; //$NON-NLS-1$

	public ProjectLocationProjectVariable() {
		// nothing
	}

	public String getValue(String variable, IProject project) {
		return project.getLocation().toPortableString();
	}

	public Object[] getExtensions(String variable, IProject project) {
		return null;
	}

}
