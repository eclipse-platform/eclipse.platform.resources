/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.tests.internal.resources;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.resources.ResourceSchedulingRule;
import org.eclipse.core.resources.*;
import org.eclipse.core.tests.harness.EclipseWorkspaceTest;

/**
 * Tests the behaviour of resource scheduling rules
 */
public class ResourceSchedulingRuleTest extends EclipseWorkspaceTest {
	public static Test suite() {
		return new TestSuite(ResourceSchedulingRuleTest.class);
	}
	public ResourceSchedulingRuleTest() {
		super();
	}
	public ResourceSchedulingRuleTest(String name) {
		super(name);
	}
	public String[] defineHierarchy() {
		return new String[] { "/", "1/", "1/1", "1/2/", "1/2/1", "1/2/2/", "2/", "2/1", "2/2/", "2/2/1", "2/2/2/" };
	}
	/**
	* Do static tests of the isConflicting method.
	*/
	public void testStatic() {
		IResource[] resources = buildResources();
		ResourceSchedulingRule[] rules = buildRules(resources);
		//test all pairs of rules
		for (int i = 0; i < resources.length; i++) {
			for (int j = 0; j < resources.length; j++) {
				boolean overlapping = isOverlapping(resources[i], resources[j]);
				assertTrue("i,j=" + i + ',' + j, overlapping == rules[i].isConflicting(rules[j]));
			}
		}
	}
	private boolean isOverlapping(IResource resource1, IResource resource2) {
		return isParent(resource1, resource2) || isParent(resource2, resource1);
	}
	private boolean isParent(IResource resource1, IResource resource2) {
		IResource parent = resource1;
		while (parent != null)  {
			if (parent.equals(resource2))
				return true;
			parent = parent.getParent();
		}
		return false;
	}
	private ResourceSchedulingRule[] buildRules(IResource[] resources) {
		ResourceSchedulingRule[] rules = new ResourceSchedulingRule[resources.length];
		for (int i = 0; i < rules.length; i++) {
			rules[i] = new ResourceSchedulingRule(resources[i]);
		}
		return rules;
	}
}
