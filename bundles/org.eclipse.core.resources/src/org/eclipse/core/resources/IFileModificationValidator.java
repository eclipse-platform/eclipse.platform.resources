package org.eclipse.core.resources;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
/**
 * <b>Note:</b> This class/interface is part of an interim API that is still under 
 * development and expected to change significantly before reaching stability. 
 * It is being made available at this early stage to solicit feedback from pioneering 
 * adopters on the understanding that any code that uses this API will almost 
 * certainly be broken (repeatedly) as the API evolves.
 * 
 * The file modification validator is a VCM-related hook for pre-checking operations 
 * that modify the contents of files.
 * <p>
 * This interface is used only in conjunction with the
 * "org.eclipse.core.resources.fileModificationValidator"
 * extension point. It is intended to be implemented only 
 * by the Eclipse Platform VCM plug-in.
 * </p>
 *
 * @since 2.0
 */
public interface IFileModificationValidator {
/**
 * Validates that the given files can be modified.  The files must all exist
 * in the workspace.  The optional context object may be supplied if
 * UI-based validation is required.  If the context is null, the validator
 * must attempt to perform the validation in a headless manner.
 * The returned status is <code>IStatus.OK</code> if this validator 
 * believes the given file can be modified.  Other return statuses indicate
 * the reason why the individual files cannot be modified.
 * 
 * @see IWorkspace#validateEdit
 */
public IStatus validateEdit(IFile[] files, Object context);
/**
 * Validates that the given file can be saved.  This method is called from 
 * <code>IFile#setContents</code> and <code>IFile#appendContents</code> 
 * before any attempt to write data to disk.  The returned status is 
 * <code>IStatus.OK</code> if this validator believes the given file can be 
 * successfully saved.  In all other cases the return value is a non-OK status.  
 * Note that a return value of <code>IStatus.OK</code> does not guarantee 
 * that the save will succeed.
 * 
 * @return a status indicating whether or not it is reasonable to try writing to the given file.
 *	A return value with an <code>IStatus.OK</code> code indicates a save should be attempted.
 * 
 * @see IFile#setContents
 * @see IFile#appendContents
 */
public IStatus validateSave(IFile file);
}