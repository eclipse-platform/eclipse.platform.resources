/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.properties;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.internal.localstore.BucketTree;
import org.eclipse.core.internal.localstore.AbstractBucketIndex.Entry;
import org.eclipse.core.internal.properties.PropertyIndex.PropertyEntry;
import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

public class PropertyManager2 implements IPropertyManager {

	private BucketTree tree;
	private File baseLocation;

	public PropertyManager2(Workspace workspace) {
		baseLocation = workspace.getMetaArea().getPropertyStoreLocation(workspace.getRoot()).toFile();
		this.tree = new BucketTree(baseLocation, createPropertyIndex());
	}

	private PropertyIndex createPropertyIndex() {
		return new PropertyIndex(baseLocation);
	}

	public void closePropertyStore(IResource target) throws CoreException {
		tree.getCurrent().save();
	}

	public void copy(IResource source, IResource destination, int depth) throws CoreException {
		copyProperties(source.getFullPath(), destination.getFullPath(), depth);
	}
	/**
	 * Copies all properties from the source path to the target path, to the given depth.
	 */
	private void copyProperties(final IPath source, final IPath destination, int depth) throws CoreException {
		int segmentsToDrop = source.segmentCount();
		final PropertyIndex destinationBucket = createPropertyIndex();		
		tree.accept(new PropertyIndex.IVisitor() {
			private IPath lastPath;

			private boolean ensureLoaded(IPath newPath) {
				IPath tmpLastPath = lastPath;
				lastPath = newPath;
				if (tmpLastPath != null && tmpLastPath.removeLastSegments(1).equals(newPath.removeLastSegments(1)))
					// still in the same source bucket, nothing to do
					return true;
				// need to load the destination bucket 
				// figure out where we want to copy the states for this path with:
				// destinationBucket = baseDestinationLocation + blob - filename - baseSourceLocation
				IPath sourceDir = Path.fromOSString(sourceBucket.getLocation().toString());
				IPath destinationDir = baseDestinationLocation.append(sourceDir.removeFirstSegments(baseSourceLocation.segmentCount()));
				try {
					destinationBucket.load(destinationDir.toFile());
				} catch (CoreException e) {
					ResourcesPlugin.getPlugin().getLog().log(e.getStatus());
					// abort traversal
					return false;
				}
				return true;
			}
			
			public int visit(Entry entry) {
				if (!ensureLoaded(sourceEntry.getPath()))
					return STOP;				
				PropertyEntry sourceEntry = (PropertyEntry) entry; 
				IPath destinationPath = destination.append(sourceEntry.getPath().removeFirstSegments(source.segmentCount()));
				PropertyEntry destinationEntry = new PropertyEntry(destinationPath, sourceEntry);
				destinationBucket.setProperties(destinationEntry);
				return CONTINUE;
			}
		}, target.getFullPath(), BucketTree.DEPTH_ZERO);		
	}

	public void deleteProperties(IResource target, int depth) throws CoreException {
		tree.accept(new PropertyIndex.IVisitor() {
			public int visit(PropertyIndex.Entry entry) {
				entry.delete();
				return CONTINUE;
			}
		}, target.getFullPath(), depth == IResource.DEPTH_INFINITE ? BucketTree.DEPTH_INFINITE : depth);
	}

	public void deleteResource(IResource target) throws CoreException {
		// TODO Auto-generated method stub

	}

	public Map getProperties(IResource target) throws CoreException {
		final Map result = new HashMap();		
		tree.accept(new PropertyIndex.IVisitor() {
			public int visit(Entry entry) {
				PropertyEntry propertyEntry = (PropertyEntry) entry; 
				int propertyCount = propertyEntry.getOccurrences();
				for (int i = 0; i < propertyCount; i++)
					result.put(propertyEntry.getPropertyName(i), propertyEntry.getPropertyValue(i));
				return CONTINUE;
			}
		}, target.getFullPath(), BucketTree.DEPTH_ZERO);
		return result;
	}

	public String getProperty(IResource target, QualifiedName name) throws CoreException {
		IPath resourcePath = target.getFullPath();
		PropertyIndex current = (PropertyIndex) tree.getCurrent();
		File indexDir = tree.locationFor(resourcePath);
		current.load(indexDir);
		return current.getProperty(resourcePath, name);
	}

	public void setProperty(IResource target, QualifiedName name, String value) throws CoreException {
		if (value != null && value.length() > 2 * 1024) {
			String message = Policy.bind("properties.valueTooLong"); //$NON-NLS-1$
			throw new ResourceException(IResourceStatus.FAILED_WRITE_METADATA, target.getFullPath(), message, null);
		}
		IPath resourcePath = target.getFullPath();
		PropertyIndex current = (PropertyIndex) tree.getCurrent();
		File indexDir = tree.locationFor(resourcePath);
		current.load(indexDir);
		current.setProperty(resourcePath, name, value);
		current.save();
	}

	public void shutdown(IProgressMonitor monitor) throws CoreException {
		tree.close();
	}

	public void startup(IProgressMonitor monitor) throws CoreException {
		// TODO Auto-generated method stub
	}

}
