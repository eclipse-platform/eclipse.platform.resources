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

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;
import org.eclipse.core.internal.localstore.Bucket;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;

public class PropertyBucket extends Bucket {

	public static class PropertyEntry extends Entry {

		private final static Comparator COMPARATOR = new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((String[]) o1)[0].compareTo(((String[]) o2)[0]);
			}
		};
		private static final String[][] EMPTY_DATA = new String[0][];
		private String[][] value;

		/**
		 * Deletes the property with the given name, and returns the result array. Returns the original 
		 * array if the property to be deleted could not be found. Returns nul if the property was found
		 * and the original array had size 1.
		 */
		public static String[][] delete(String[][] existing, String propertyName) {
			// a size-1 array is a special case
			if (existing.length == 1)
				return (existing[0][0].equals(propertyName)) ? null : existing;
			// find the guy to delete
			int deletePosition = search(existing, propertyName);
			if (deletePosition < 0)
				// not found, nothing to delete
				return existing;
			String[][] newValue = new String[existing.length - 1][];
			if (deletePosition > 0)
				// copy elements preceding the one to be removed
				System.arraycopy(existing, 0, newValue, 0, deletePosition);
			if (deletePosition < existing.length - 1)
				// copy elements succeeding the one to be removed
				System.arraycopy(existing, deletePosition + 1, newValue, deletePosition, newValue.length - deletePosition);
			return newValue;
		}

		public static String[][] insert(String[][] existing, String propertyName, String propertyValue) {
			// look for the right spot where to insert the new guy
			int index = search(existing, propertyName);
			if (index >= 0) {
				// found existing occurrence - just replace the value
				existing[index][1] = propertyValue;
				return existing;
			}
			// not found - insert 
			int insertPosition = -index - 1;
			String[][] newValue = new String[existing.length + 1][];
			if (insertPosition > 0)
				System.arraycopy(existing, 0, newValue, 0, insertPosition);
			newValue[insertPosition] = new String[] {propertyName, propertyValue};
			if (insertPosition < existing.length)
				System.arraycopy(existing, insertPosition, newValue, insertPosition + 1, existing.length - insertPosition);
			return newValue;
		}

		private static int search(String[][] existing, String propertyName) {
			return Arrays.binarySearch(existing, new String[] {propertyName, null}, COMPARATOR);
		}

		public PropertyEntry(IPath path, PropertyEntry base) {
			super(path);
			this.value = new String[base.value.length][];
			System.arraycopy(base.value, 0, this.value, 0, this.value.length);
		}

		protected PropertyEntry(IPath path, String[][] value) {
			super(path);
			this.value = value;
		}

		/**
		 * Compacts the data array removing any null slots. If non-null slots
		 * are found, the entry is marked for removal. 
		 */
		void compact() {
			if (!isDirty())
				return;
			int occurrences = 0;
			for (int i = 0; i < value.length; i++)
				if (value[i] != null)
					value[occurrences++] = value[i];
			if (occurrences == value.length)
				// no states deleted
				return;
			if (occurrences == 0) {
				// no states remaining
				value = EMPTY_DATA;
				delete();
				return;
			}
			String[][] result = new String[occurrences][];
			System.arraycopy(value, 0, result, 0, occurrences);
			value = result;
		}

		public int getOccurrences() {
			return value == null ? 0 : value.length;
		}

		public String getProperty(QualifiedName name) {
			int index = search(value, name.toString());
			return index < 0 ? null : value[index][1];
		}

		public Object getPropertyName(int i) {
			return this.value[i][0];
		}

		public Object getPropertyValue(int i) {
			return this.value[i][1];
		}

		public Object getValue() {
			return value;
		}

		public void visited() {
			compact();
		}

		/**
		 * Merges two entries (are always sorted). Duplicated additions replace existing ones.
		 */
		static Object merge(String[][] base, String[][] additions) {
			int additionPointer = 0;
			int basePointer = 0;
			int added = 0;
			String[][] result = new String[base.length + additions.length][];
			while (basePointer < base.length && additionPointer < additions.length) {
				int comparison = base[basePointer][0].compareTo(additions[additionPointer][0]);
				if (comparison == 0) {
					result[added++] = additions[additionPointer++];
					// duplicate, override
					basePointer++;
				} else if (comparison < 0)
					result[added++] = base[basePointer++];
				else
					result[added++] = additions[additionPointer++];
			}
			// copy the remaining states from either additions or base arrays
			String[][] remaining = basePointer == base.length ? additions : base;
			int remainingPointer = basePointer == base.length ? additionPointer : basePointer;
			int remainingCount = remaining.length - remainingPointer;
			System.arraycopy(remaining, remainingPointer, result, added, remainingCount);
			added += remainingCount;
			if (added == base.length + additions.length)
				// no collisions
				return result;
			// there were collisions, need to compact
			String[][] finalResult = new String[added][];
			System.arraycopy(result, 0, finalResult, 0, finalResult.length);
			return finalResult;
		}
	}

	/** Version number for the current implementation file's format.
	 * <p>
	 * Version 1:
	 * <pre>
	 * FILE ::= VERSION_ID ENTRY+
	 * ENTRY ::= PATH PROPERTY_COUNT PROPERTY+
	 * PATH ::= string
	 * PROPERTY_COUNT ::= int
	 * PROPERTY ::= KEY VALUE
	 * KEY ::= string
	 * UUID	 ::= byte[16]
	 * LAST_MODIFIED ::= byte[8]
	 * </pre>
	 * </p>
	 */
	private static final byte VERSION = 1;

	public PropertyBucket(File root) {
		super(root);
	}

	protected Entry createEntry(IPath path, Object value) {
		return new PropertyEntry(path, (String[][]) value);
	}

	private PropertyEntry getEntry(IPath path) {
		String pathAsString = path.toString();
		String[][] existing = (String[][]) getEntryValue(pathAsString);
		if (existing == null)
			return null;
		return new PropertyEntry(path, existing);
	}

	public String getProperty(IPath path, QualifiedName name) {
		PropertyEntry entry = getEntry(path);
		if (entry == null)
			return null;
		return entry.getProperty(name);
	}

	protected byte getVersion() {
		return VERSION;
	}

	protected Object readEntryValue(DataInputStream source) throws IOException {
		int length = source.readUnsignedShort();
		String[][] properties = new String[length][2];
		for (int j = 0; j < properties.length; j++) {
			properties[j][0] = source.readUTF();
			properties[j][1] = source.readUTF();
		}
		return properties;
	}

	public void setProperties(PropertyEntry entry) {
		IPath path = entry.getPath();
		String[][] additions = (String[][]) entry.getValue();
		String pathAsString = path.toString();
		String[][] existing = (String[][]) getEntryValue(pathAsString);
		if (existing == null) {
			setEntryValue(pathAsString, additions);
			return;
		}
		setEntryValue(pathAsString, PropertyEntry.merge(existing, additions));
	}

	public void setProperty(IPath path, QualifiedName name, String value) {
		String pathAsString = path.toString();
		String nameAsString = name.toString();
		String[][] existing = (String[][]) getEntryValue(pathAsString);
		if (existing == null) {
			if (value != null)
				setEntryValue(pathAsString, new String[][] { {nameAsString, value}});
			return;
		}
		String[][] newValue;
		if (value != null)
			newValue = PropertyEntry.insert(existing, nameAsString, value);
		else
			newValue = PropertyEntry.delete(existing, nameAsString);
		// even if newValue == existing we should mark as dirty (insert may not create a new array)
		setEntryValue(pathAsString, newValue);
	}

	protected void writeEntryValue(DataOutputStream destination, Object entryValue) throws IOException {
		String[][] properties = (String[][]) entryValue;
		destination.writeShort(properties.length);
		for (int j = 0; j < properties.length; j++) {
			destination.writeUTF(properties[j][0]);
			destination.writeUTF(properties[j][1]);
		}
	}
}
