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
import java.util.*;
import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.internal.resources.ResourceStatus;
import org.eclipse.core.internal.utils.Assert;
import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.*;

public abstract class Bucket {

	public static abstract class Entry {
		/**
		 * This entry has not been modified in any way so far.
		 * 
		 * @see #state
		 */
		private final static int STATE_CLEAR = 0;
		/**
		 * This entry has been requested for deletion.
		 * 
		 * @see #state
		 */
		private final static int STATE_DELETED = 0x02;
		/**
		 * This entry has been modified.
		 * 
		 * @see #state
		 */
		private final static int STATE_DIRTY = 0x01;

		/**
		 * Logical path of the object we are storing history for. This does not
		 * correspond to a file system path.
		 */
		private IPath path;

		/**
		 * State for this entry. Possible values are STATE_CLEAR, STATE_DIRTY and STATE_DELETED.
		 * 
		 * @see #STATE_CLEAR
		 * @see #STATE_DELETED
		 * @see #STATE_DIRTY
		 */
		private byte state = STATE_CLEAR;

		protected Entry(IPath path) {
			this.path = path;
		}

		public void delete() {
			state = STATE_DELETED;
		}

		public abstract int getOccurrences();

		public IPath getPath() {
			return path;
		}

		public abstract Object getValue();

		public boolean isDeleted() {
			return state == STATE_DELETED;
		}

		public boolean isDirty() {
			return state == STATE_DIRTY;
		}

		public boolean isEmpty() {
			return getOccurrences() == 0;
		}

		public void markDirty() {
			Assert.isTrue(state != STATE_DELETED);
			state = STATE_DIRTY;
		}

		/**
		 * Called on the entry right after the visitor has visited it.
		 *
		 */
		public void visited() {
			// does not do anything by default
		}
	}

	public static abstract class Visitor {
		// should continue the traversal
		public final static int CONTINUE = 0;
		// should stop looking at states for files in this container (or any of its children)	
		public final static int RETURN = 2;
		// should stop the traversal	
		public final static int STOP = 1;

		/**
		 * Called after the bucket has been visited (and saved). 
		 */
		public void afterSaving(Bucket bucket) throws CoreException {
			// empty implementation, subclasses to override
		}

		/** 
		 * @return either STOP, CONTINUE or RETURN
		 */
		public abstract int visit(Entry entry);
	}

	private static final String BUCKET = "bucket.index"; //$NON-NLS-1$

	private static final int CHANGES_THRESHOLD = 100;

	private static final byte DELETE_CHANGE = 2;

	private static final byte REPLACE_CHANGE = 1;

	/**
	 * The offset for the append area. -1 if unknown (no file has been loaded).
	 */
	private int appendAreaOffset = -1;

	/**
	 * Whether there are too many changes in the append area and it should be collapsed.
	 */
	private boolean appendAreaOverflow;
	/**
	 * List of changes since the last time this bucket was saved.
	 */
	private final List changes;

	/**
	 * Map of the history entries in this bucket. Maps (String -> byte[][]),
	 * where the key is the path of the object we are storing history for, and
	 * the value is the history entry data (UUID,timestamp) pairs.
	 */
	private final Map entries;
	/**
	 * The file system location of this bucket index file.
	 */
	private File location;

	/**
	 * The root directory of the bucket indexes on disk.
	 */
	private File root;

	public Bucket(File root) {
		this.root = root;
		this.entries = new HashMap();
		this.changes = new LinkedList();
	}

	/**
	 * Applies the given visitor to this bucket index. 
	 * @param visitor
	 * @param filter
	 * @param depth the number of trailing segments that can differ from the filter 
	 * @return one of STOP, RETURN or CONTINUE constants
	 * @throws CoreException
	 */
	public final int accept(Visitor visitor, IPath filter, int depth) throws CoreException {
		if (entries.isEmpty())
			return Visitor.CONTINUE;
		try {
			for (Iterator i = entries.entrySet().iterator(); i.hasNext();) {
				Map.Entry mapEntry = (Map.Entry) i.next();
				IPath path = new Path((String) mapEntry.getKey());
				// check whether the filter applies
				int matchingSegments = filter.matchingFirstSegments(path);
				if (!filter.isPrefixOf(path) || path.segmentCount() - matchingSegments > depth)
					continue;
				// apply visitor
				Entry bucketEntry = createEntry(path, mapEntry.getValue());
				// calls the visitor passing all uuids for the entry				
				int outcome = visitor.visit(bucketEntry);
				// compact the entry in case any changes have happened
				bucketEntry.visited();
				if (bucketEntry.isDeleted()) {
					i.remove();
					changes.add(mapEntry.getKey());
				} else if (bucketEntry.isDirty()) {
					mapEntry.setValue(bucketEntry.getValue());
					changes.add(mapEntry.getKey());
				}
				if (outcome != Visitor.CONTINUE)
					return outcome;
			}
			return Visitor.CONTINUE;
		} finally {
			save();
			visitor.afterSaving(this);
		}
	}

	protected abstract Entry createEntry(IPath path, Object value);

	/**
	 * Tries to delete as many empty levels as possible.
	 */
	private void delete(File toDelete) {
		// don't try to delete beyond the root for bucket indexes
		if (toDelete.equals(root))
			return;
		if (toDelete.delete())
			// if deletion went fine, try deleting the parent dir			
			delete(toDelete.getParentFile());
	}

	private void fullSave() throws IOException {
		// ensure the parent location exists 
		location.getParentFile().mkdirs();
		DataOutputStream destination = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(location), 8192));
		try {
			destination.write(getVersion());
			destination.writeInt(entries.size());
			for (Iterator i = entries.entrySet().iterator(); i.hasNext();) {
				Map.Entry entry = (Map.Entry) i.next();
				destination.writeUTF((String) entry.getKey());
				writeEntryValue(destination, entry.getValue());
			}
		} finally {
			destination.close();
		}
	}

	public final int getEntryCount() {
		return entries.size();
	}

	public final Object getEntryValue(String path) {
		return entries.get(path);
	}

	public File getLocation() {
		return location == null ? null : location.getParentFile();
	}

	protected abstract byte getVersion();

	private void incrementalSave() throws IOException {
		DataOutputStream destination = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(location, true), 8192));
		try {
			for (Iterator i = changes.iterator(); i.hasNext();) {
				String path = (String) i.next();
				Object entryValue = getEntryValue(path);
				byte changeTag = entryValue == null ? DELETE_CHANGE : REPLACE_CHANGE;
				destination.writeByte(changeTag);
				destination.writeUTF(path);
				if (entryValue != null)
					writeEntryValue(destination, entryValue);
			}
		} finally {
			destination.close();
		}
	}

	public final void load(File baseLocation) throws CoreException {
		load(baseLocation, false);
	}

	public final void load(File baseLocation, boolean force) throws CoreException {
		try {
			// avoid reloading
			if (!force && this.location != null && baseLocation.equals(this.location.getParentFile()))
				return;
			// previously loaded bucket may not have been saved... save before loading new one
			save();
			this.location = new File(baseLocation, BUCKET);
			this.entries.clear();
			if (!this.location.isFile())
				return;
			DataInputStream source = new DataInputStream(new BufferedInputStream(new FileInputStream(location), 8192));
			try {
				int version = source.readByte();
				if (version != getVersion()) {
					// unknown version
					String message = Policy.bind("resources.readMetaWrongVersion", location.getAbsolutePath(), Integer.toString(version)); //$NON-NLS-1$
					ResourceStatus status = new ResourceStatus(IResourceStatus.FAILED_READ_METADATA, message);
					throw new ResourceException(status);
				}
				int entryCount = source.readInt();
				for (int i = 0; i < entryCount; i++)
					this.entries.put(source.readUTF(), readEntryValue(source));
				// now read any remaining incremental changes
				int changeTag;
				int changeCount = 0;
				while ((changeTag = source.read()) != -1) {
					changeCount++;
					if (changeTag == REPLACE_CHANGE)
						this.entries.put(source.readUTF(), readEntryValue(source));
					else
						this.entries.remove(source.readUTF());
				}
				if (changeCount > CHANGES_THRESHOLD)
					appendAreaOverflow = true;
			} finally {
				source.close();
			}
		} catch (IOException ioe) {
			String message = Policy.bind("resources.readMeta", location.getAbsolutePath()); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.FAILED_READ_METADATA, null, message, ioe);
			throw new ResourceException(status);
		}
	}

	protected abstract Object readEntryValue(DataInputStream source) throws IOException;

	public final void save() throws CoreException {
		if (changes.isEmpty())
			return;
		try {
			if (entries.isEmpty()) {
				changes.clear();
				delete(location);
				return;
			}
			if (appendAreaOverflow || changes.size() > CHANGES_THRESHOLD || !location.isFile())
				fullSave();
			else
				incrementalSave();
			changes.clear();
		} catch (IOException ioe) {
			String message = Policy.bind("resources.writeMeta", location.getAbsolutePath()); //$NON-NLS-1$
			ResourceStatus status = new ResourceStatus(IResourceStatus.FAILED_WRITE_METADATA, null, message, ioe);
			throw new ResourceException(status);
		}
	}

	public final void setEntryValue(String path, Object value) {
		if (value == null) {
			entries.remove(path);
		} else
			entries.put(path, value);
		changes.add(path);
	}

	protected abstract void writeEntryValue(DataOutputStream destination, Object entryValue) throws IOException;
}
