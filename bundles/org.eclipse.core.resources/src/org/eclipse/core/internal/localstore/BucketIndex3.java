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
import org.eclipse.core.internal.utils.UniversalUniqueIdentifier;
import org.eclipse.core.runtime.*;

public class BucketIndex3 {

	public abstract static class BatchVisitor extends Visitor {
		public void newBucket(BucketIndex3 newBucket) {
			// don't do anything
		}

		public final int visit(IPath path, byte[] uuid) {
			throw new UnsupportedOperationException();
		}

		/** 
		 * @return either STOP, CONTINUE or RETURN and optionally DELETE
		 */
		public abstract int visit(IPath path, byte[][] uuids);
	}

	public class Entry implements Comparable {
		IPath filePath;
		byte[] uuid;

		Entry(IPath path, byte[] uuid) {
			this.filePath = path;
			this.uuid = uuid;
		}

		public int compareTo(Object o) {
			return -UniversalUniqueIdentifier.compareTime(new UniversalUniqueIdentifier(this.uuid), new UniversalUniqueIdentifier(((Entry) o).uuid));
		}
	}

	//	private static class RecyclableBufferedInputStream extends BufferedInputStream {
	//		private byte[] savedBuffer;
	//		private final static int BUFFER_SIZE = 8 * 1024;
	//		private boolean closed = false;
	//
	//		public RecyclableBufferedInputStream() {
	//			super(new ByteArrayInputStream(new byte[0]), BUFFER_SIZE);
	//			savedBuffer = this.buf;
	//			try {
	//				this.close();
	//			} catch (IOException e) {
	//				// never gonna happen with these type of streams
	//			}
	//		}
	//
	//		public void reload(InputStream stream) {
	//			if (!closed)
	//				throw new IllegalStateException();
	//			this.buf = savedBuffer;
	//			this.count = 0;
	//			this.in = stream;
	//			this.marklimit = 0;
	//			this.markpos = -1;
	//			this.closed = false;
	//		}
	//
	//		public void close() throws IOException {
	//			this.closed = true;
	//			super.close();
	//		}
	//	}
	//
	//	private static class RecyclableBufferedOutputStream extends BufferedOutputStream {
	//		private byte[] savedBuffer;
	//		private boolean closed;
	//
	//		public RecyclableBufferedOutputStream() {
	//			super(new ByteArrayOutputStream(0), RecyclableBufferedInputStream.BUFFER_SIZE);
	//			savedBuffer = this.buf;
	//			try {
	//				this.close();
	//			} catch (IOException e) {
	//				// never gonna happen with these type of streams
	//			}
	//		}
	//
	//		public void reload(OutputStream stream) {
	//			if (!closed)
	//				throw new IllegalStateException();
	//			this.buf = savedBuffer;
	//			this.count = 0;
	//			this.out = stream;
	//		}
	//
	//		public void close() throws IOException {
	//			this.closed = true;
	//			super.close();
	//		}
	//	}

	public abstract static class Visitor {
		// should stop the traversal
		public final static int CONTINUE = 0;
		// should delete this entry (can be combined with the other constants)
		public final static int DELETE = 0x100;
		// should stop looking at states for files in this container (or any of its children)	
		public final static int RETURN = 2;
		// should stop looking at states for this file		
		public final static int SKIP_FILE = 3;
		// keep visiting, still happy	
		public final static int STOP = 1;

		/** 
		 * @return either STOP, CONTINUE, RETURN or SKIP_FILE and optionally DELETE
		 */
		public abstract int visit(IPath path, byte[] uuid);
	}

	private static final String BUCKET = ".bucket"; //$NON-NLS-1$

	//	private static final int UUID_LENGTH = new UniversalUniqueIdentifier().toString().length();

	//	private static RecyclableBufferedInputStream bufferedInputStream = new RecyclableBufferedInputStream();

	//	private static RecyclableBufferedOutputStream bufferedOutputStream = new RecyclableBufferedOutputStream();
	private Map entries;
	private File location;
	private boolean needSaving = false;

	private File root;

	private static int indexOf(byte[][] array, byte[] item) {
		// look for existing occurrences
		for (int i = 0; i < array.length; i++)
			if (UniversalUniqueIdentifier.equals(item, array[i]))
				return i;
		return -1;
	}

	public BucketIndex3(File root) {
		this.root = root;
		this.entries = new HashMap();
	}

	/**
	 * 
	 * @param visitor
	 * @param filter
	 * @return one of STOP, RETURN or CONTINUE constants
	 * @throws CoreException
	 */
	public int accept(Visitor visitor, IPath filter, boolean exactMatch, boolean sorted) throws CoreException {
		if (entries.isEmpty())
			return Visitor.CONTINUE;
		try {
			if (visitor instanceof BatchVisitor)
				return batchAccept((BatchVisitor) visitor, filter, exactMatch, sorted);
			for (Iterator i = entries.entrySet().iterator(); i.hasNext();) {
				Map.Entry entry = (Map.Entry) i.next();
				IPath path = new Path((String) entry.getKey());
				// check whether the filter applies
				if (!filter.isPrefixOf(path) || (exactMatch && !filter.equals(path)))
					continue;
				// calls the visitor for every uuid in the entry
				byte[][] uuids = (byte[][]) entry.getValue();
				int deleted = 0;
				for (int j = 0; j < uuids.length; j++) {
					int outcome = visitor.visit(path, uuids[j]);
					if ((outcome & Visitor.DELETE) != 0) {
						needSaving = true;
						// mark that uuid as deleted
						uuids[j] = null;
						byte[][]newValue = concatUUIDs(uuids);
						if (newValue == null)
							i.remove();
						else
							entry.setValue(newValue);
					}
					if ((outcome & Visitor.SKIP_FILE) != 0)
						// skip the remaining states for this file
						break;
					if ((outcome & Visitor.RETURN) != 0)
						// skip any other buckets under this
						return Visitor.RETURN;
					if ((outcome & Visitor.STOP) != 0)
						// stop looking					
						return Visitor.STOP;
				}
			}
			return Visitor.CONTINUE;
		} finally {
			save();
		}
	}

	public void addBlob(IPath path, byte[] uuid) {
		String pathAsString = path.toString();
		byte[][] existing = (byte[][]) entries.get(pathAsString);
		if (existing == null) {
			entries.put(pathAsString, new byte[][] {uuid});
			needSaving = true;
			return;
		}
		// look for existing occurrences
		if (contains(existing, uuid))
			// already there - nothing else to be done
			return;
		byte[][] newValue = new byte[existing.length + 1][];
		System.arraycopy(existing, 0, newValue, 0, existing.length);
		newValue[newValue.length - 1] = uuid;
		sortUUIDs(newValue);
		entries.put(pathAsString, newValue);
		needSaving = true;
	}

	public void addBlob(IPath path, UniversalUniqueIdentifier uuid) {
		addBlob(path, uuid.toBytes());
	}

	public void addBlobs(IPath path, byte[][] uuids) {
		String pathAsString = path.toString();
		byte[][] existing = (byte[][]) entries.get(pathAsString);
		if (existing == null) {
			entries.put(pathAsString, uuids);
			needSaving = true;
			return;
		}
		// add after looking for existing occurrences
		List newUUIDs = new ArrayList(existing.length + uuids.length);
		for (int i = 0; i < uuids.length; i++)
			if (!contains(existing, uuids[i]))
				newUUIDs.add(uuids[i]);
		if (newUUIDs.isEmpty())
			// none added
			return;
		byte[][] newValue = new byte[existing.length + newUUIDs.size()][];
		newUUIDs.toArray(newValue);
		System.arraycopy(existing, 0, newValue, newUUIDs.size(), existing.length);
		entries.put(pathAsString, newValue);
		needSaving = true;
	}

	private int batchAccept(BatchVisitor visitor, IPath filter, boolean exactMatch, boolean sorted) throws CoreException {
		visitor.newBucket(this);
		for (Iterator i = entries.entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = (Map.Entry) i.next();
			IPath path = new Path((String) entry.getKey());
			// check whether the filter applies
			if (!filter.isPrefixOf(path) || (exactMatch && !filter.equals(path)))
				continue;
			// calls the visitor passing all uuids for the entry
			byte[][] uuids = (byte[][]) entry.getValue();
			int outcome = visitor.visit(path, uuids);
			if ((outcome & Visitor.DELETE) != 0) {
				needSaving = true;
				i.remove();
			}
			if ((outcome & Visitor.RETURN) != 0)
				// skip any other buckets under this
				return Visitor.RETURN;
			if ((outcome & Visitor.STOP) != 0)
				// stop looking
				return Visitor.STOP;
		}
		return Visitor.CONTINUE;
	}

	private byte[][] concatUUIDs(byte[][] uuids) {
		int found = 0;
		for (int i = 0; i < uuids.length; i++)
			if (uuids[i] != null)
				found++;
		if (found == 0)
			return null;
		if (found == uuids.length)
			return uuids;
		byte[][] result = new byte[found][];
		int copied = 0;
		for (int i = 0; i < uuids.length; i++)
			if (uuids[i] != null)
				result[copied++] = uuids[i];
		return result;
	}

	private boolean contains(byte[][] array, byte[] item) {
		return indexOf(array, item) >= 0;
	}

	/**
	 * Tries to delete as many empty levels as possible.
	 */
	private void delete(File toDelete) {
		// don't try to delete the root for bucket indexes
		if (toDelete.equals(root))
			return;
		if (toDelete.delete())
			// if deletion went fine, try deleting the parent dir			
			delete(toDelete.getParentFile());
	}

	File getLocation() {
		return location == null ? null : location.getParentFile();
	}

	public byte[][] getUUIDs(IPath path) {
		String pathAsString = path.toString();
		byte[][] existing = (byte[][]) entries.get(pathAsString);
		if (existing == null)
			return new byte[0][];
		sortUUIDs(existing);
		return existing;
	}

	public void load(File baseLocation) throws CoreException {
		try {
			// avoid reloading
			if (this.location != null && baseLocation.equals(this.location.getParentFile()))
				return;
			// previously loaded bucket may not have been saved... save before loading new one
			save();
			this.location = new File(baseLocation, BUCKET);
			this.entries.clear();
			if (!this.location.isFile())
				return;
			//bufferedInputStream.reload(new FileInputStream(location));
			//DataInputStream source = new DataInputStream(bufferedInputStream);
			DataInputStream source = new DataInputStream(new BufferedInputStream(new FileInputStream(location), 8192));
			try {
				int entryCount = source.readInt();
				for (int i = 0; i < entryCount; i++) {
					String key = source.readUTF();
					int length = source.readUnsignedShort();
					byte[][] uuids = new byte[length][UniversalUniqueIdentifier.BYTES_SIZE];
					for (int j = 0; j < uuids.length; j++)
						source.read(uuids[j]);
					this.entries.put(key, uuids);
				}
			} finally {
				source.close();
			}
		} catch (IOException ioe) {
			//TODO
			throw new ResourceException(0, null, "", ioe);
		}
	}

	public void save() throws CoreException {
		if (!needSaving)
			return;
		try {
			if (entries.isEmpty()) {
				needSaving = false;
				delete(location);
				return;
			}
			// ensure the parent location exists 
			location.getParentFile().mkdirs();
			//			bufferedOutputStream.reload(new FileOutputStream(location));
			//			DataOutputStream destination = new DataOutputStream(bufferedOutputStream);
			DataOutputStream destination = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(location), 8192));
			try {
				destination.writeInt(entries.size());
				for (Iterator i = entries.entrySet().iterator(); i.hasNext();) {
					Map.Entry entry = (Map.Entry) i.next();
					destination.writeUTF((String) entry.getKey());
					byte[][] uuids = (byte[][]) entry.getValue();
					destination.writeShort(uuids.length);
					for (int j = 0; j < uuids.length; j++)
						destination.write(uuids[j]);
				}
			} finally {
				destination.close();
			}
			needSaving = false;
		} catch (IOException ioe) {
			throw new ResourceException(0, null, "", ioe);
		}
	}

	private void sortUUIDs(byte[][] uuids) {
		Arrays.sort(uuids, new Comparator() {
			public int compare(Object o1, Object o2) {
				return -UniversalUniqueIdentifier.compareTime((byte[]) o1, (byte[]) o2);
			}
		});
	}
}
