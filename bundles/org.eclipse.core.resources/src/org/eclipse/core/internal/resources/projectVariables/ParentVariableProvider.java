package org.eclipse.core.internal.resources.projectVariables;

import java.util.*;
import org.eclipse.core.internal.utils.Messages;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectVariableProvider;
import org.eclipse.core.runtime.IPath;

/**
 * Path Variable representing the parent directory of the variable provided
 * in argument, following the syntax:
 * 
 * "${PARENT-COUNT-MyVariable}"
 *
 */
public class ParentVariableProvider implements IProjectVariableProvider {

	final public static String NAME = "PARENT"; //$NON-NLS-1$

	public ParentVariableProvider() {
		// nothing
	}

	public Object[] getExtensions(String variable, IProject project) {
		LinkedList result = new LinkedList();
		Iterator it = Arrays.asList(project.getPathVariableManager().getPathVariableNames()).iterator();
		while(it.hasNext()) {
			String value = (String) it.next();
			if (!value.equals("PARENT"))  		//$NON-NLS-1$
				result.add("1-" + value); 	//$NON-NLS-1$
		}
		return result.toArray();
	}

	public String getValue(String variable, IProject project) {
		int index = variable.indexOf('-');
		if (index == -1 || index == (variable.length() -1))
			return Messages.parentVariableProvider_noVariableSpecified;
		
		String countRemaining = variable.substring(index + 1);
		index = countRemaining.indexOf('-');
		if (index == -1 || index == (variable.length() -1))
			return Messages.parentVariableProvider_noVariableSpecified;

		String countString = countRemaining.substring(0, index);
		int count = 0;
		try {
			count = Integer.parseInt(countString);
			if (count < 0)
				return Messages.parentVariableProvider_noVariableSpecified;
		}catch (NumberFormatException e) {
			return Messages.parentVariableProvider_noVariableSpecified;
		}
		String argument = countRemaining.substring(index + 1);
		
		IPath value = project.getPathVariableManager().getValue(argument);
		if (value == null)
			return null;
		value = project.getPathVariableManager().resolvePath(value);
		value = value.removeLastSegments(count);
			
		return value.toPortableString();
	}

}
