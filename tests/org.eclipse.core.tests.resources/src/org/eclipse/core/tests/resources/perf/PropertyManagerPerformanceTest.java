/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.resources.perf;

import java.util.*;
import java.util.ArrayList;
import java.util.List;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.tests.harness.PerformanceTestRunner;
import org.eclipse.core.tests.resources.ResourceTest;

public class PropertyManagerPerformanceTest extends ResourceTest {

	public PropertyManagerPerformanceTest() {
		super(null);
	}

	public PropertyManagerPerformanceTest(String name) {
		super(name);
	}

	public static Test suite() {
		//			TestSuite suite = new TestSuite();
		//			suite.addTest(new PropertyManagerTest("testProperties"));
		//			return suite;
		return new TestSuite(PropertyManagerPerformanceTest.class);
	}

	public static String getPropertyValue(int size) {
		StringBuffer value = new StringBuffer(size);
		for (int i = 0; i < size; i++)
			value.append((char) (Math.random() * Character.MAX_VALUE));
		return value.toString();
	}

	public void testSetProperty() {
		IProject proj1 = getWorkspace().getRoot().getProject("proj1");
		final IFolder folder1 = proj1.getFolder("folder1");
		new PerformanceTestRunner() {
			List allResources = createTree(folder1, 2000);
			protected void test() {
				for (Iterator i = allResources.iterator(); i.hasNext();) {
					IResource resource = (IResource) i.next();
					try {
						resource.setPersistentProperty(new QualifiedName("qualifier", "prop" + ((int) Math.random() * 50)), getPropertyValue(2048));
					} catch (CoreException ce) {
						fail("0.2", ce);
					}
				}
			}

			protected void tearDown() {
				try {
					((Workspace) getWorkspace()).getPropertyManager().deleteProperties(folder1, IResource.DEPTH_INFINITE);
				} catch (CoreException e) {
					fail("0.1", e);
				}
			}
		}.run(this, 1, 1);
	}

	/**
	 * Creates a tree of resources containing history. 
	 */
	private List createTree(IFolder base, int filesPerFolder) {
		IFolder[] folders = new IFolder[5];
		folders[0] = base.getFolder("folder1");
		folders[1] = base.getFolder("folder2");
		folders[2] = folders[0].getFolder("folder3");
		folders[3] = folders[2].getFolder("folder4");
		folders[4] = folders[3].getFolder("folder5");
		List resources = new ArrayList(filesPerFolder * folders.length);
		for (int i = 0; i < folders.length; i++)
			resources.add(folders[i]);
		ensureExistsInWorkspace(folders, true);
		for (int i = 0; i < folders.length; i++) {
			for (int j = 0; j < filesPerFolder; j++) {
				IFile file = folders[i].getFile("file" + j);
				ensureExistsInWorkspace(file, getRandomContents());
				resources.add(file);
			}
		}
		return resources;
	}

}