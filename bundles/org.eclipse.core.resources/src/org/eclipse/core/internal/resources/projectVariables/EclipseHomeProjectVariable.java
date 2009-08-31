/*******************************************************************************
 * Copyright (c) 2008, 2009 Freescale Semiconductor and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Freescale Semiconductor - initial API and implementation
 *     IBM Corporation - ongoing development
 *******************************************************************************/
package org.eclipse.core.internal.resources.projectVariables;

import java.net.URL;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.PathVariableResolver;
import org.eclipse.core.runtime.*;

/**
 * ECLIPSE_HOME project variable, pointing to the location of the eclipse home
 * 
 */
public class EclipseHomeProjectVariable extends PathVariableResolver {

	public EclipseHomeProjectVariable() {
		// nothing to do.
	}

	public String getValue(String variable, IProject project) {
		URL installURL = Platform.getInstallLocation().getURL();
		IPath ppath = new Path(installURL.getFile()).removeTrailingSeparator();
		return ppath.toPortableString();
	}

	public Object[] getExtensions(String variable, IProject project) {
		return null;
	}

}
