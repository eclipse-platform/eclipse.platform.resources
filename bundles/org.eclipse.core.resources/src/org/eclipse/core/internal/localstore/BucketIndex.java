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
package org.eclipse.core.internal.localstore;

import java.io.*;
import java.io.File;
import java.util.*;
import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.internal.utils.UniversalUniqueIdentifier;
import org.eclipse.core.runtime.*;

public class BucketIndex {

	class BlobInfo implements Comparable {
		IPath filePath;
		String uuid;

		BlobInfo(IPath path, String uuid) {
			this.filePath = path;
			this.uuid = uuid;
		}

		public int compareTo(Object o) {
			return -UniversalUniqueIdentifier.compareTime(new UniversalUniqueIdentifier(this.uuid), new UniversalUniqueIdentifier(((BlobInfo) o).uuid));
		}
	}

	private static final int UUID_LENGTH = new UniversalUniqueIdentifier().toString().length();
	private Properties entries;
	private File location;
	private boolean needSaving = false;

	public void addBlob(IPath path, UniversalUniqueIdentifier uuid) {
		needSaving = true;
		String pathAsString = path.toString();
		String uuidAsString = uuid.toString();
		String existing = (String) entries.get(pathAsString);
		if (existing == null) {
			entries.put(pathAsString, uuidAsString);
			return;
		}
		// look for existing occurrences
		int occurrences = existing.length() / uuidAsString.length();
		for (int i = 0; i < occurrences; i++)
			if (existing.regionMatches(i * UUID_LENGTH, uuidAsString, 0, UUID_LENGTH))
				// found it - nothing else to be done
				return;
		entries.put(pathAsString, existing + uuidAsString);
	}

	public BlobInfo[] findChildren(IPath containerPath) {
		List result = new ArrayList();
		for (Iterator i = entries.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry) i.next();
			String path = (String) entry.getKey();
			if (!containerPath.isPrefixOf(new Path(path)))
				continue;
			String[] uuids = parseUUIDs((String) entry.getValue());
			for (int j = 0; j < uuids.length; j++)
				result.add(new BlobInfo(new Path(path), uuids[j]));
		}
		return (BlobInfo[]) result.toArray(new BlobInfo[result.size()]);
	}

	public String[] getUUIDs(IPath path) {
		String pathAsString = path.toString();
		String existing = (String) entries.get(pathAsString);
		if (existing == null)
			return new String[0];
		return parseUUIDs(existing);
	}

	public void load(File baseLocation) throws CoreException {
		try {
			// avoid reloading
			if (this.location != null && baseLocation.equals(this.location.getParentFile()))
				return;
			// previously loaded bucket may not have been saved... save before loading new one
			save();
			this.location = new File(baseLocation, ".index");
			this.entries = new Properties();
			if (!this.location.isFile())
				return;
			InputStream source = new BufferedInputStream(new FileInputStream(location), 2048);
			try {
				entries.load(source);
			} finally {
				source.close();
			}
		} catch (IOException ioe) {
			//TODO
			throw new ResourceException(0, null, "", ioe);
		}
	}

	private String[] parseUUIDs(String existing) {
		int occurrences = existing.length() / UUID_LENGTH;
		String[] result = new String[occurrences];
		for (int i = 0; i < occurrences; i++)
			result[i] = existing.substring(i * UUID_LENGTH, (i + 1) * UUID_LENGTH);
		return result;
	}

	public void remove(IPath path, UniversalUniqueIdentifier uuid) {
		String pathAsString = path.toString();
		String uuidAsString = uuid.toString();
		String existing = (String) entries.get(pathAsString);
		if (existing == null)
			// path not found
			return;
		int position = -1;
		int occurrences = existing.length() / uuidAsString.length();
		for (int i = 0; i < occurrences; i++)
			if (existing.regionMatches(i * UUID_LENGTH, uuidAsString, 0, UUID_LENGTH)) {
				// found it
				position = i * UUID_LENGTH;
				break;
			}
		if (position == -1)
			// uuid not found			
			return;
		needSaving = true;
		// there was only one left - delete the entry
		if (existing.length() == UUID_LENGTH) {
			entries.remove(pathAsString);
			return;
		}
		// otherwise, update the content for the entry
		StringBuffer newValue = new StringBuffer(existing);
		newValue.delete(position, position + UUID_LENGTH);
		entries.put(pathAsString, newValue.toString());
	}

	public void save() throws CoreException {
		if (!needSaving)
			return;
		try {
			if (entries.isEmpty()) {
				location.delete();
				return;
			}
			OutputStream destination = new BufferedOutputStream(new FileOutputStream(location), 2048);
			try {
				entries.store(destination, null);
			} finally {
				destination.close();
			}
			needSaving = false;
		} catch (IOException ioe) {
			throw new ResourceException(0, null, "", ioe);
		}
	}

	public void clear() {
		this.location = null;
		this.entries = new Properties();
		this.needSaving = false;
	}

}
