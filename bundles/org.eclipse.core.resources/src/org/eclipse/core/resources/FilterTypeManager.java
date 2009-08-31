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

import org.eclipse.core.filesystem.IFileInfoFilter;

import org.eclipse.core.runtime.CoreException;

import java.util.HashMap;
import org.eclipse.core.runtime.*;

/**
 *  This class collects all the FilterTypeFactories along with their properties.
 */
public class FilterTypeManager {

	private static final String FILTER_PROVIDER = "filterProvider";  //$NON-NLS-1$
	
	public static final String ARGUMENT_TYPE_NONE = "none"; //$NON-NLS-1$
	public static final String ARGUMENT_TYPE_STRING = "string"; //$NON-NLS-1$
	public static final String ARGUMENT_TYPE_FILTERS = "filters"; //$NON-NLS-1$
	public static final String ARGUMENT_TYPE_FILTER = "filter"; //$NON-NLS-1$

	public class Descriptor {
		private String id;
		private String name;
		private String description;
		private String argumentType;
		private IFileInfoFilterFactory factory;
		private boolean isFirst = false;
		
		public Descriptor(IConfigurationElement element) throws CoreException {
			this(element, true);
		}
		
		public Descriptor(IConfigurationElement element, boolean instantiateFactory) throws CoreException {
			id = element.getAttribute("id"); //$NON-NLS-1$
			name = element.getAttribute("name"); //$NON-NLS-1$
			description = element.getAttribute("description"); //$NON-NLS-1$
			argumentType = element.getAttribute("argumentType"); //$NON-NLS-1$
			if (argumentType == null)
				argumentType = ARGUMENT_TYPE_NONE;
			if (instantiateFactory)
				factory = (IFileInfoFilterFactory ) element.createExecutableExtension("class"); //$NON-NLS-1$
			String ordering = element.getAttribute("ordering"); //$NON-NLS-1$
			if (ordering != null)
				isFirst = ordering.equals("first"); //$NON-NLS-1$
		}
		public String getId() {
			return id;
		}
		public String getName() {
			return name;
		}
		public String getDescription() {
			return description;
		}
		public String getArgumentType() {
			return argumentType;
		}
		public IFileInfoFilterFactory getFactory() {
			return factory;
		}
		public boolean isFirstOrdering() {
			return isFirst;
		}
	}
	
	private HashMap/*<String, Descriptor>*/  factories = new HashMap();
	private static FilterTypeManager instance = null;

	static public FilterTypeManager getDefault() {
		if (instance == null)
			instance = new FilterTypeManager();
		return instance;
	}
	
	
	protected FilterTypeManager() {
		IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(ResourcesPlugin.PI_RESOURCES,ResourcesPlugin.PT_FILTER_PROVIDERS);
		if (point != null) {
			IExtension[] ext = point.getExtensions();
			// initial population
			for (int i = 0; i < ext.length; i++) {
				IExtension extension = ext[i];
				processExtension(extension);
			}
			Platform.getExtensionRegistry().addListener(new IRegistryEventListener() {
				public void added(IExtension[] extensions) {
					for (int i = 0; i < extensions.length; i++)
						processExtension(extensions[i]);
				}
				public void added(IExtensionPoint[] extensionPoints) {
					// nothing to do
				}
				public void removed(IExtension[] extensions) {
					for (int i = 0; i < extensions.length; i++)
						processRemovedExtension(extensions[i]);
				}
				public void removed(IExtensionPoint[] extensionPoints) {
					// nothing to do
				}
			});
		}
	}
	
	protected void processExtension(IExtension extension) {
		IConfigurationElement[] elements = extension.getConfigurationElements();
		for (int i = 0; i < elements.length; i++) {
			IConfigurationElement element = elements[i];
			if (element.getName().equalsIgnoreCase(FILTER_PROVIDER)) {
				try {
					Descriptor desc = new Descriptor(element);
					factories.put(desc.getId(), desc);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected void processRemovedExtension(IExtension extension) {
		IConfigurationElement[] elements = extension.getConfigurationElements();
		for (int i = 0; i < elements.length; i++) {
			IConfigurationElement element = elements[i];
			if (element.getName().equalsIgnoreCase(FILTER_PROVIDER)) {
				try {
					Descriptor desc = new Descriptor(element, false);
					factories.remove(desc.getId());
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public Descriptor[] getDescriptors() {
		return (Descriptor[]) factories.values().toArray(new Descriptor[0]);
	}
	
	public IFileInfoFilter instantiate(String id, IProject project, String arguments) {
		Object obj = factories.get(id);
		if (obj != null)
			return ((Descriptor) obj).getFactory().instantiate(project, arguments);
		return null;
	}

	public Descriptor findDescriptor(String id) {
		return (Descriptor) factories.get(id);
	}
}
