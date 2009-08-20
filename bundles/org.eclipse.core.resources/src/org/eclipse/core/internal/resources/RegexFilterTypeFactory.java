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
package org.eclipse.core.internal.resources;

import org.eclipse.core.resources.IFilterTypeFactory;

import java.util.regex.Matcher;

import java.util.regex.Pattern;

import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.resources.IProject;

import org.eclipse.core.resources.IFilterType;

/**
 * A Filter provider for Java Regular expression supported by 
 * java.util.regex.Pattern.
 */
public class RegexFilterTypeFactory implements IFilterTypeFactory {

	static class RegexFilterType implements IFilterType {
		Pattern pattern = null;
	
		public RegexFilterType(IProject project, String arguments) {
			if (arguments != null)
				pattern = Pattern.compile(arguments);
		}
		public boolean matches(IFileInfo fileInfo) {
			 Matcher m = pattern.matcher(fileInfo.getName());
			 return m.matches();
		}
	}

	public IFilterType instantiate(IProject project, String arguments) {
		return new RegexFilterType(project, arguments);
	}
}
