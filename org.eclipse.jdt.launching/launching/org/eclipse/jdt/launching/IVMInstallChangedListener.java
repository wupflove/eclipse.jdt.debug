package org.eclipse.jdt.launching;

import org.eclipse.jdt.core.IJavaProject;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

/**
 * A VM install changed listener is notified 
 * the workspace default VM install changes.
 * Listeners register with <code>JavaRuntime</code>.
 * <p>
 * Clients may implement this interface.
 * </p>
 * @since 2.0
 */
public interface IVMInstallChangedListener {
		
	/**
	 * Notification that the workspace default VM install
	 * has changed.
	 * 
	 * @param previous the VM install that was previously assigned
	 * 	to the workspace, possibly <code>null</code>
	 * @param current the VM install that is currently assigned to the
	 * 	workspace, possibly <code>null</code>
	 */
	public void defaultVMInstallChanged(IVMInstall previous, IVMInstall current);	

}
