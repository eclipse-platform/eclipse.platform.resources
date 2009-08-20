/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Serge Beauchamp (Freescale Semiconductor) - initial API and implementation
 *     James Blackburn - Test fix for bug 266712
 *******************************************************************************/
package org.eclipse.core.tests.resources;

import org.eclipse.core.filesystem.URIUtil;

import java.io.*;
import java.net.URI;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.filesystem.*;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

/**
 * Tests the following API methods: IFolder#createGroup
 * 
 */
public class GroupResourceTest extends ResourceTest {
	protected String childName = "File.txt";
	protected IProject closedProject;
	protected IFile existingFileInExistingProject;
	protected IFolder existingFolderInExistingFolder;
	protected IFolder existingFolderInExistingProject;
	protected IFolder existing_GROUP_InExistingProject;
	protected IProject existingProject;
	protected IPath localFile;
	protected IPath localFolder;
	protected IFile nonExistingFileInExistingFolder;
	protected IFile nonExistingFileInExistingGroup;
	protected IFile nonExistingFileInExistingProject;
	protected IFile nonExistingFileInOtherExistingProject;
	protected IFolder nonExistingFolderInExistingFolder;
	protected IFolder nonExistingFolderInExistingGroup;
	protected IFolder nonExisting_GROUP_InExistingFolder;
	protected IFolder nonExisting_GROUP_InExistingGroup;
	protected IFolder nonExistingFolderInExistingProject;
	protected IFolder nonExisting_GROUP_InExistingProject;
	protected IFolder nonExistingFolderInNonExistingFolder;
	protected IFolder nonExisting_GROUP_InNonExistingFolder;
	protected IFolder nonExistingFolderInNonExistingProject;
	protected IFolder nonExisting_GROUP_InNonExistingProject;
	protected IFolder nonExistingFolderInOtherExistingProject;
	protected IPath nonExistingLocation;
	protected IProject nonExistingProject;
	protected IProject otherExistingProject;

	public static Test suite() {
		return new TestSuite(GroupResourceTest.class);
		// TestSuite suite = new TestSuite();
		// suite.addTest(new
		// GroupResourceTest("testCreateProjectWithDeepLinks"));
		// return suite;
	}

	public GroupResourceTest() {
		super();
	}

	public GroupResourceTest(String name) {
		super(name);
	}

	protected void doCleanup() throws Exception {

		ensureExistsInWorkspace(new IResource[] { existingProject,
				otherExistingProject, closedProject,
				existingFolderInExistingProject,
				existingFolderInExistingFolder, existingFileInExistingProject,
				existing_GROUP_InExistingProject }, true);
		closedProject.close(getMonitor());
		ensureDoesNotExistInWorkspace(new IResource[] { nonExistingProject,
				nonExistingFolderInExistingProject,
				nonExistingFolderInExistingFolder,
				nonExistingFolderInOtherExistingProject,
				nonExistingFolderInNonExistingProject,
				nonExistingFolderInNonExistingFolder,
				nonExistingFileInExistingProject,
				nonExistingFileInOtherExistingProject,
				nonExistingFileInExistingFolder,
				nonExistingFileInExistingGroup,
				nonExistingFolderInExistingGroup,
				nonExisting_GROUP_InExistingGroup,
				nonExisting_GROUP_InExistingProject,
				nonExisting_GROUP_InNonExistingFolder,
				nonExisting_GROUP_InNonExistingProject });
		ensureDoesNotExistInFileSystem(resolve(nonExistingLocation).toFile());
		resolve(localFolder).toFile().mkdirs();
		createFileInFileSystem(resolve(localFile), getRandomContents());
	}

	private byte[] getFileContents(IFile file) throws CoreException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		transferData(new BufferedInputStream(file.getContents()), bout);
		return bout.toByteArray();
	}

	/**
	 * Maybe overridden in subclasses that use path variables.
	 */
	protected IPath resolve(IPath path) {
		return path;
	}

	/**
	 * Maybe overridden in subclasses that use path variables.
	 */
	protected URI resolve(URI uri) {
		return uri;
	}

	protected void setUp() throws Exception {
		super.setUp();
		existingProject = getWorkspace().getRoot()
				.getProject("ExistingProject");
		otherExistingProject = getWorkspace().getRoot().getProject(
				"OtherExistingProject");
		closedProject = getWorkspace().getRoot().getProject("ClosedProject");
		existingFolderInExistingProject = existingProject
				.getFolder("existingFolderInExistingProject");
		existing_GROUP_InExistingProject = existingProject
				.getFolder("existing_GROUP_InExistingProject");
		existing_GROUP_InExistingProject = existingProject
				.getFolder("existing_GROUP_InExistingProject");

		existingFolderInExistingFolder = existingFolderInExistingProject
				.getFolder("existingFolderInExistingFolder");
		nonExistingFolderInExistingProject = existingProject
				.getFolder("nonExistingFolderInExistingProject");
		nonExisting_GROUP_InExistingProject = existingProject
				.getFolder("nonExisting_GROUP_InExistingProject");
		nonExistingFolderInOtherExistingProject = otherExistingProject
				.getFolder("nonExistingFolderInOtherExistingProject");
		nonExistingFolderInNonExistingFolder = nonExistingFolderInExistingProject
				.getFolder("nonExistingFolderInNonExistingFolder");
		nonExisting_GROUP_InNonExistingFolder = nonExistingFolderInExistingProject
				.getFolder("nonExisting_GROUP_InNonExistingFolder");
		nonExistingFolderInExistingFolder = existingFolderInExistingProject
				.getFolder("nonExistingFolderInExistingFolder");
		nonExistingFolderInExistingGroup = existing_GROUP_InExistingProject
				.getFolder("nonExistingFolderInExistingGroup");
		nonExisting_GROUP_InExistingGroup = existing_GROUP_InExistingProject
				.getFolder("nonExisting_GROUP_InExistingGroup");
		nonExisting_GROUP_InExistingFolder = existingFolderInExistingProject
				.getFolder("nonExisting_GROUP_InExistingFolder");

		nonExistingProject = getWorkspace().getRoot().getProject("NonProject");
		nonExistingFolderInNonExistingProject = nonExistingProject
				.getFolder("nonExistingFolderInNonExistingProject");
		nonExisting_GROUP_InNonExistingProject = nonExistingProject
				.getFolder("nonExisting_GROUP_InNonExistingProject");

		existingFileInExistingProject = existingProject
				.getFile("existingFileInExistingProject");
		nonExistingFileInExistingProject = existingProject
				.getFile("nonExistingFileInExistingProject");
		nonExistingFileInOtherExistingProject = otherExistingProject
				.getFile("nonExistingFileInOtherExistingProject");
		nonExistingFileInExistingFolder = existingFolderInExistingProject
				.getFile("nonExistingFileInExistingFolder");
		nonExistingFileInExistingGroup = existing_GROUP_InExistingProject
				.getFile("nonExistingFileInExistingGroup");
		localFolder = getRandomLocation();
		nonExistingLocation = getRandomLocation();
		localFile = localFolder.append(childName);
		doCleanup();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
		Workspace.clear(resolve(localFolder).toFile());
		Workspace.clear(resolve(nonExistingLocation).toFile());
	}

	/**
	 * This test creates a group resource
	 */
	public void testCreateGroup() {
		ensureDoesNotExistInWorkspace(nonExisting_GROUP_InExistingProject);
		IFolder folder = nonExisting_GROUP_InExistingProject;
		try {
			folder.createGroup(0, getMonitor());
		} catch (CoreException e) {
			fail("0.99", e);
		}

		assertTrue("1.0", folder.exists());
		assertTrue("1.1", folder.isGroup());

		// delete should succeed
		try {
			folder.delete(IResource.NONE, getMonitor());
		} catch (CoreException e) {
			fail("1.2", e);
		}
	}

	/**
	 * This test creates a file under a group resource
	 */
	public void testCreateFileUnderGroup() {
		ensureExistsInWorkspace(existing_GROUP_InExistingProject, true);
		IFile file = nonExistingFileInExistingGroup;
		boolean failed = false;
		try {
			create(file, true);
		} catch (CoreException e) {
			failed = true;
		}

		assertTrue("2.0", existing_GROUP_InExistingProject.exists());
		assertTrue("2.1", existing_GROUP_InExistingProject.isGroup());
		assertTrue("2.2", !file.exists());
		assertTrue("2.3", failed);
	}

	/**
	 * This test creates a file under a group resource
	 */
	public void testCreateFolderUnderGroup() {
		ensureDoesNotExistInWorkspace(existing_GROUP_InExistingProject);
		IFolder folder = nonExistingFolderInExistingGroup;
		boolean failed = false;
		try {
			create(folder, true);
		} catch (CoreException e) {
			failed = true;
		}

		assertTrue("2.0", existing_GROUP_InExistingProject.exists());
		assertTrue("2.1", existing_GROUP_InExistingProject.isGroup());
		assertTrue("2.2", !folder.exists());
		assertTrue("2.3", failed);
	}

	/**
	 * This test creates a file under a group resource
	 */
	public void testCreateGroupUnderGroup() {
		ensureDoesNotExistInWorkspace(existing_GROUP_InExistingProject);
		IFolder group = nonExisting_GROUP_InExistingGroup;
		try {
			create(group, true);
		} catch (CoreException e) {
			fail("3.0", e);
		}

		assertTrue("3.1", existing_GROUP_InExistingProject.exists());
		assertTrue("3.2", existing_GROUP_InExistingProject.isGroup());
		assertTrue("3.3", group.exists());
		assertTrue("3.4", group.isGroup());

		// delete should succeed
		try {
			group.delete(IResource.NONE, getMonitor());
		} catch (CoreException e) {
			fail("3.5", e);
		}
	}

	/**
	 * Tests creation of a linked folder under a group
	 */
	public void testCreateLinkedFolderUnderGroup() {
		ensureExistsInWorkspace(existing_GROUP_InExistingProject, true);
		// get a non-existing location
		IPath location = getRandomLocation();
		IFolder folder = nonExistingFolderInExistingGroup;

		try {
			folder.createLink(location, IResource.ALLOW_MISSING_LOCAL,
					getMonitor());
		} catch (CoreException e) {
			fail("4.0", e);
		}

		assertTrue("4.1", folder.exists());
		assertEquals("4.2", resolve(location), folder.getLocation());
		assertTrue("4.3", !resolve(location).toFile().exists());

		// getting children should succeed (and be empty)
		try {
			assertEquals("4.4", 0, folder.members().length);
		} catch (CoreException e) {
			fail("4.5", e);
		}
		// delete should succeed
		try {
			folder.delete(IResource.NONE, getMonitor());
		} catch (CoreException e) {
			fail("4.6", e);
		}
	}

	/**
	 * Tests creation of a linked folder under a group
	 */
	public void testCreateLinkedFileUnderGroup() {
		ensureExistsInWorkspace(existing_GROUP_InExistingProject, true);

		// get a non-existing location
		IPath location = getRandomLocation();
		IFile file = nonExistingFileInExistingGroup;

		try {
			file.createLink(location, IResource.ALLOW_MISSING_LOCAL,
					getMonitor());
		} catch (CoreException e) {
			fail("5.0", e);
		}

		assertTrue("5.1", file.exists());
		assertEquals("5.2", resolve(location), file.getLocation());
		assertTrue("5.3", !resolve(location).toFile().exists());

		// delete should succeed
		try {
			file.delete(IResource.NONE, getMonitor());
		} catch (CoreException e) {
			fail("5.6", e);
		}
	}

	public void testCopyProjectWithGroups() {
		ensureExistsInWorkspace(existing_GROUP_InExistingProject, true);

		IPath fileLocation = getRandomLocation();
		IFile linkedFile = nonExistingFileInExistingGroup;
		IFolder linkedFolder = nonExistingFolderInExistingGroup;
		try {
			try {
				createFileInFileSystem(resolve(fileLocation),
						getRandomContents());
				linkedFolder.createLink(localFolder, IResource.NONE,
						getMonitor());
				linkedFile.createLink(fileLocation, IResource.NONE,
						getMonitor());
			} catch (CoreException e) {
				fail("1.0", e);
			}

			// copy the project
			IProject destination = getWorkspace().getRoot().getProject("CopyTargetProject");
			try {
				existingProject.copy(destination.getFullPath(), IResource.SHALLOW, getMonitor());
			} catch (CoreException e) {
				fail("2.0", e);
			}

			IFile newFile = destination.getFile(linkedFile
					.getProjectRelativePath());
			assertTrue("3.0", newFile.isLinked());
			assertEquals("3.1", linkedFile.getLocation(), newFile.getLocation());
			assertTrue("3.2", newFile.getParent().isGroup());

			IFolder newFolder = destination.getFolder(linkedFolder
					.getProjectRelativePath());
			assertTrue("4.0", newFolder.isLinked());
			assertEquals("4.1", linkedFolder.getLocation(), newFolder
					.getLocation());
			assertTrue("4.2", newFolder.getParent().isGroup());

			// test project deep copy, it should give the same results since
			// their parents are a group too.
			try {
				destination.delete(IResource.NONE, getMonitor());
				existingProject.copy(destination.getFullPath(), IResource.NONE,
						getMonitor());
			} catch (CoreException e) {
				fail("5.0", e);
			}
			assertTrue("5.1", newFile.isLinked());
			assertEquals("5.2", linkedFile.getLocation(), newFile.getLocation());
			assertTrue("5.3", newFile.getParent().isGroup());
			assertTrue("5.4", newFolder.isLinked());
			assertEquals("5.5", linkedFolder.getLocation(), newFolder
					.getLocation());
			assertTrue("5.6", newFolder.getParent().isGroup());

			try {
				destination.delete(IResource.NONE, getMonitor());
			} catch (CoreException e) {
				fail("5.99", e);
			}
		} finally {
			Workspace.clear(resolve(fileLocation).toFile());
		}
	}

	public void testMoveProjectWithGroups() {
		ensureExistsInWorkspace(existing_GROUP_InExistingProject, true);
		IPath fileLocation = getRandomLocation();
		IFile file = nonExistingFileInExistingGroup;
		IFolder folder = nonExistingFolderInExistingGroup;
		IFile childFile = folder.getFile(childName);
		IResource[] oldResources = new IResource[] { file, folder,
				existingProject, childFile };
		try {
			assertDoesNotExistInWorkspace("0.9", new IResource[] { folder,
					file, childFile });

			try {
				createFileInFileSystem(resolve(fileLocation));
				folder.createLink(localFolder, IResource.ALLOW_MISSING_LOCAL,
						getMonitor());
				file.createLink(fileLocation, IResource.ALLOW_MISSING_LOCAL,
						getMonitor());
			} catch (CoreException e) {
				fail("1.0", e);
			}

			assertTrue("3.3", folder.getParent().isGroup());

			// move the project
			IProject destination = getWorkspace().getRoot().getProject(
					"MoveTargetProject");
			IFile newFile = destination.getFile(file.getProjectRelativePath());
			IFolder newFolder = destination.getFolder(folder
					.getProjectRelativePath());
			IFile newChildFile = newFolder.getFile(childName);
			IResource[] newResources = new IResource[] { destination, newFile,
					newFolder, newChildFile };

			assertDoesNotExistInWorkspace("2.0", destination);

			try {
				existingProject.move(destination.getFullPath(), IResource.SHALLOW,
						getMonitor());
			} catch (CoreException e) {
				fail("2.1", e);
			}
			assertExistsInWorkspace("3.0", newResources);
			assertDoesNotExistInWorkspace("3.1", oldResources);
			assertTrue("3.2", existingProject
					.isSynchronized(IResource.DEPTH_INFINITE));
			assertTrue("3.23", destination
					.isSynchronized(IResource.DEPTH_INFINITE));

			assertTrue("3.3", newFile.getParent().isGroup());
			assertTrue("3.4", newFile.isLinked());

			assertTrue("3.6", newFolder.isLinked());
			assertTrue("3.7", newFolder.getParent().isGroup());

			assertTrue("3.8", destination
					.isSynchronized(IResource.DEPTH_INFINITE));
		} finally {
			Workspace.clear(resolve(fileLocation).toFile());
		}
	}

	/**
	 * Tests deleting and then recreating a project
	 */
	public void testDeleteProjectWithGroup() {
		IFolder group = nonExisting_GROUP_InExistingProject;
		try {
			create(group, true);
			existingProject.delete(IResource.NEVER_DELETE_PROJECT_CONTENT,
					getMonitor());
			existingProject.create(getMonitor());
		} catch (CoreException e) {
			fail("0.99", e);
		}

		// group should not exist until the project is open
		assertTrue("1.0", !group.exists());

		try {
			existingProject.open(getMonitor());
		} catch (CoreException e) {
			fail("1.99", e);
		}

		// group should now exist
		assertTrue("2.0", group.exists());
		assertTrue("2.1", group.isGroup());
	}

	/**
	 * Tests deleting and then recreating a project
	 */
	public void testDeleteProjectWithGroupAndLink() {
		IFolder group = nonExisting_GROUP_InExistingProject;
		IFolder link = group.getFolder("a_link");
		try {
			create(group, true);
			link.createLink(localFolder, IResource.NONE, getMonitor());
			existingProject.delete(IResource.NEVER_DELETE_PROJECT_CONTENT,
					getMonitor());
			existingProject.create(getMonitor());
		} catch (CoreException e) {
			fail("0.99", e);
		}

		// group should not exist until the project is open
		assertTrue("1.0", !group.exists());
		assertTrue("1.0", !link.exists());

		try {
			existingProject.open(getMonitor());
		} catch (CoreException e) {
			fail("1.99", e);
		}

		// group should now exist
		assertTrue("2.0", group.exists());
		assertTrue("2.1", group.isGroup());
		// link should now exist
		assertTrue("2.0", link.exists());
		assertTrue("2.1", link.isLinked());
		assertEquals("2.2", resolve(localFolder), link.getLocation());
	}

	/**
	 * Test EFS access to group children
	 */
	public void testEFSFileStore() {
		IPath location = getRandomLocation();
		IFolder folder = nonExistingFolderInExistingGroup;

		try {
			folder.createLink(location, IResource.ALLOW_MISSING_LOCAL,
					getMonitor());
		} catch (CoreException e) {
			fail("4.0", e);
		}

		assertTrue("4.1", folder.exists());
		assertEquals("4.2", resolve(location), folder.getLocation());
		assertTrue("4.3", !resolve(location).toFile().exists());

		// Check non-null EFS access
		try {
			IFileStore fs = EFS.getStore(existing_GROUP_InExistingProject.getLocationURI());
			fs = fs.getChild(folder.getName());
			assertNotNull(fs);
			assertNotNull(fs.toURI());
		} catch (CoreException e) {
			fail("6.0", e);
		}
	}

	/**
	 * Tests the {@link org.eclipse.core.resources.IResource#isGroup()} method.
	 */
	public void testIsGroup() {
		IResource[] toTest = new IResource[] { closedProject,
				existingFileInExistingProject, existingFolderInExistingFolder,
				existingFolderInExistingProject, existingProject,
				nonExistingFileInExistingFolder,
				nonExistingFileInExistingProject,
				nonExistingFileInOtherExistingProject,
				nonExistingFolderInExistingFolder,
				nonExistingFolderInExistingProject,
				nonExistingFolderInNonExistingFolder,
				nonExistingFolderInNonExistingProject,
				nonExistingFolderInOtherExistingProject, nonExistingProject,
				otherExistingProject };
		for (int i = 0; i < toTest.length; i++) {
			assertTrue("1.0 " + toTest[i], !toTest[i].isGroup());
		}
		// create a group
		IFolder group = nonExisting_GROUP_InExistingProject;
		try {
			create(group, true);
		} catch (CoreException e) {
			fail("1.99", e);
		}
		IFile child = group.getFile(childName);
		assertTrue("2.0", !child.exists());
		assertTrue("2.1", group.isGroup());
		assertTrue("2.1", !child.isGroup());
	}
	
	public void testIsLocal() {
		// create a group
		IFolder group = nonExisting_GROUP_InExistingProject;
		try {
			create(group, true);
		} catch (CoreException e) {
			fail("1.99", e);
		}
		assertTrue("1.0", group.isLocal(IResource.DEPTH_INFINITE));
		assertTrue("1.1", group.getParent().isLocal(IResource.DEPTH_INFINITE));
	}

	
	public void testIsSynchronized() {
		// create a group
		IFolder group = nonExisting_GROUP_InExistingProject;
		try {
			create(group, true);
		} catch (CoreException e) {
			fail("1.99", e);
		}
		assertTrue("1.0", group.isSynchronized(IResource.DEPTH_INFINITE));
		assertTrue("1.1", group.getParent().isSynchronized(IResource.DEPTH_INFINITE));
	}

	/**
	 * Specific testing of group within links.
	 */
	public void testLinkedFileInLinkedFolder() {
		// setup handles
		IProject project = existingProject;
		IFolder top = project.getFolder("topFolder");
		IFolder linkedFolder = top.getFolder("linkedFolder");
		IFolder subFolder = linkedFolder.getFolder("subFolder");
		IFolder group = subFolder.getFolder("group");
		IFileStore folderStore = getTempStore();
		IFileStore subFolderStore = folderStore.getChild(subFolder.getName());
		IFileStore fileStore = getTempStore();
		IPath folderLocation = URIUtil.toPath(folderStore.toURI());

		try {
			// create the structure on disk
			subFolderStore.mkdir(EFS.NONE, getMonitor());
			fileStore.openOutputStream(EFS.NONE, getMonitor()).close();

			// create the structure in the workspace
			ensureExistsInWorkspace(top, true);
			linkedFolder.createLink(folderStore.toURI(), IResource.NONE,
					getMonitor());
			group.createGroup(0, getMonitor());
		} catch (CoreException e) {
			fail("4.99", e);
		} catch (IOException e) {
			fail("4.99", e);
		}

		// assert locations
		assertEquals("1.0", folderLocation, linkedFolder.getLocation());
		assertEquals("1.1", folderLocation.append(subFolder.getName()),
				subFolder.getLocation());
		assertTrue("1.2", group.isGroup());
		// assert URIs
		assertEquals("1.0", folderStore.toURI(), linkedFolder.getLocationURI());
		assertEquals("1.1", subFolderStore.toURI(), subFolder.getLocationURI());
		assertTrue("1.2", group.getLocation() == null);
	}

	public void testMoveProjectWithGroup() {
		IPath fileLocation = getRandomLocation();
		IFile file = nonExistingFileInExistingProject;
		IFolder folder = nonExisting_GROUP_InExistingProject;
		IFile childFile = folder.getFile(childName);
		IResource[] oldResources = new IResource[] { file, folder,
				existingProject, childFile };
		try {
			try {
				createFileInFileSystem(resolve(fileLocation));
				folder.createGroup(IResource.NONE, getMonitor());
				file.createLink(fileLocation, IResource.ALLOW_MISSING_LOCAL,
						getMonitor());
				childFile.createLink(fileLocation,
						IResource.ALLOW_MISSING_LOCAL, getMonitor());
				IResource[] children = ((IContainer) folder)
						.members(IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS);
				assertTrue("0.9", children.length == 1);
			} catch (CoreException e) {
				fail("1.0", e);
			}

			assertExistsInWorkspace("1.5", new IResource[] { existingProject,
					file, folder });

			// move the project
			IProject destination = getWorkspace().getRoot().getProject(
					"MoveTargetProject");
			IFile newFile = destination.getFile(file.getProjectRelativePath());
			IFolder newFolder = destination.getFolder(folder
					.getProjectRelativePath());
			IFile newChildFile = newFolder.getFile(childName);
			IResource[] newResources = new IResource[] { destination, newFile,
					newFolder, newChildFile };

			assertDoesNotExistInWorkspace("2.0", destination);

			try {
				existingProject.move(destination.getFullPath(),
						IResource.SHALLOW, getMonitor());
			} catch (CoreException e) {
				fail("2.1", e);
			}
			assertExistsInWorkspace("3.0", newResources);
			assertDoesNotExistInWorkspace("3.1", oldResources);

			assertTrue("3.2", newFile.isLinked());
			assertEquals("3.3", resolve(fileLocation), newFile.getLocation());

			assertTrue("3.4", newFolder.isGroup());

			assertTrue("3.6", destination
					.isSynchronized(IResource.DEPTH_INFINITE));

			// now do a deep move back to the original project
			try {
				destination.move(existingProject.getFullPath(), IResource.NONE,
						getMonitor());
			} catch (CoreException e) {
				fail("5.0", e);
			}
			assertExistsInWorkspace("5.1", oldResources);
			assertDoesNotExistInWorkspace("5.2", newResources);
			assertTrue("5.3", !file.isLinked());
			assertTrue("5.4", folder.isGroup());
			// assertTrue("5.7",
			// existingProject.isSynchronized(IResource.DEPTH_INFINITE));
			// assertTrue("5.8",
			// destination.isSynchronized(IResource.DEPTH_INFINITE));
		} finally {
			Workspace.clear(resolve(fileLocation).toFile());
		}
	}
}
