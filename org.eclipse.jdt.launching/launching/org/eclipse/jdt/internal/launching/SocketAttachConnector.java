/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.launching;


import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdi.Bootstrap;
import org.eclipse.jdi.TimeoutException;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;

import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

/**
 * A standard socket attaching connector
 */
public class SocketAttachConnector implements IVMConnector {
		
	/**
	 * Return the socket transport attaching connector
	 * 
	 * @exception CoreException if unable to locate the connector
	 */
	protected static AttachingConnector getAttachingConnector() throws CoreException {
		AttachingConnector connector= null;
		Iterator iter= Bootstrap.virtualMachineManager().attachingConnectors().iterator();
		while (iter.hasNext()) {
			AttachingConnector lc= (AttachingConnector) iter.next();
			if (lc.name().equals("com.sun.jdi.SocketAttach")) { //$NON-NLS-1$
				connector= lc;
				break;
			}
		}
		if (connector == null) {
			abort(LaunchingMessages.getString("SocketAttachConnector.Socket_attaching_connector_not_available_3"), null, IJavaLaunchConfigurationConstants.ERR_SHARED_MEMORY_CONNECTOR_UNAVAILABLE); //$NON-NLS-1$
		}
		return connector;
	}

	/**
	 * @see IVMConnector#getIdentifier()
	 */
	public String getIdentifier() {
		return IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR;
	}

	/**
	 * @see IVMConnector#getName()
	 */
	public String getName() {
		return LaunchingMessages.getString("SocketAttachConnector.Standard_(Socket_Attach)_4"); //$NON-NLS-1$
	}
	
	/**
	 * Throws a core exception with an error status object built from
	 * the given message, lower level exception, and error code.
	 * 
	 * @param message the status message
	 * @param exception lower level exception associated with the
	 *  error, or <code>null</code> if none
	 * @param code error code
	 */
	protected static void abort(String message, Throwable exception, int code) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, LaunchingPlugin.getUniqueIdentifier(), code, message, exception));
	}		

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.IVMConnector#connect(java.util.Map, org.eclipse.core.runtime.IProgressMonitor, org.eclipse.debug.core.ILaunch)
	 */
	public void connect(Map arguments, IProgressMonitor monitor, ILaunch launch) throws CoreException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		
		IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 1);
		subMonitor.beginTask(LaunchingMessages.getString("SocketAttachConnector.Connecting..._1"), 2); //$NON-NLS-1$
		subMonitor.subTask(LaunchingMessages.getString("SocketAttachConnector.Configuring_connection..._1")); //$NON-NLS-1$
		
		AttachingConnector connector= getAttachingConnector();
		String portNumberString = (String)arguments.get("port"); //$NON-NLS-1$
		if (portNumberString == null) {
			abort(LaunchingMessages.getString("SocketAttachConnector.Port_unspecified_for_remote_connection._2"), null, IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_PORT); //$NON-NLS-1$
		}
		String host = (String)arguments.get("hostname"); //$NON-NLS-1$
		if (host == null) {
			abort(LaunchingMessages.getString("SocketAttachConnector.Hostname_unspecified_for_remote_connection._4"), null, IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_HOSTNAME); //$NON-NLS-1$
		}
		Map map= connector.defaultArguments();
		
        Connector.Argument param= (Connector.Argument) map.get("hostname"); //$NON-NLS-1$
		param.setValue(host);
		param= (Connector.Argument) map.get("port"); //$NON-NLS-1$
		param.setValue(portNumberString);
        
        String timeoutString = (String)arguments.get("timeout"); //$NON-NLS-1$
        if (timeoutString != null) {
            param= (Connector.Argument) map.get("timeout"); //$NON-NLS-1$
            param.setValue(timeoutString);
        }
        
		ILaunchConfiguration configuration = launch.getLaunchConfiguration();
		boolean allowTerminate = false;
		if (configuration != null) {
			allowTerminate = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_ALLOW_TERMINATE, false);
		}
		subMonitor.worked(1);
		subMonitor.subTask(LaunchingMessages.getString("SocketAttachConnector.Establishing_connection..._2")); //$NON-NLS-1$
		try {
			VirtualMachine vm = connector.attach(map);
			String vmLabel = constructVMLabel(vm, host, portNumberString, configuration);
			IDebugTarget debugTarget= JDIDebugModel.newDebugTarget(launch, vm, vmLabel, null, allowTerminate, true);
			launch.addDebugTarget(debugTarget);
			subMonitor.worked(1);
			subMonitor.done();
		} catch (UnknownHostException e) {
			abort(MessageFormat.format(LaunchingMessages.getString("SocketAttachConnector.Failed_to_connect_to_remote_VM_because_of_unknown_host___{0}__1"), new String[]{host}), e, IJavaLaunchConfigurationConstants.ERR_REMOTE_VM_CONNECTION_FAILED); //$NON-NLS-1$
		} catch (ConnectException e) {
			abort(LaunchingMessages.getString("SocketAttachConnector.Failed_to_connect_to_remote_VM_as_connection_was_refused_2"), e, IJavaLaunchConfigurationConstants.ERR_REMOTE_VM_CONNECTION_FAILED); //$NON-NLS-1$
		} catch (IOException e) {
			abort(LaunchingMessages.getString("SocketAttachConnector.Failed_to_connect_to_remote_VM_1"), e, IJavaLaunchConfigurationConstants.ERR_REMOTE_VM_CONNECTION_FAILED); //$NON-NLS-1$
		} catch (IllegalConnectorArgumentsException e) {
			abort(LaunchingMessages.getString("SocketAttachConnector.Failed_to_connect_to_remote_VM_1"), e, IJavaLaunchConfigurationConstants.ERR_REMOTE_VM_CONNECTION_FAILED); //$NON-NLS-1$
		}
	}

	/**
	 * Helper method that constructs a human-readable label for a remote VM.
	 */
	protected String constructVMLabel(VirtualMachine vm, String host, String port, ILaunchConfiguration configuration) {
		String name = null;
		try {
			name = vm.name();
		} catch (TimeoutException e) {
			// do nothing
		} catch (VMDisconnectedException e) {
			// do nothing
		}
		if (name == null) {
			if (configuration == null) {
				name = ""; //$NON-NLS-1$
			} else {
				name = configuration.getName();
			}
		}
		StringBuffer buffer = new StringBuffer(name);
		buffer.append('['); //$NON-NLS-1$
		buffer.append(host);
		buffer.append(':'); //$NON-NLS-1$
		buffer.append(port);
		buffer.append(']'); //$NON-NLS-1$
		return buffer.toString();
	}
		

	/**
	 * @see IVMConnector#getDefaultArguments()
	 */
	public Map getDefaultArguments() throws CoreException {
		Map def = getAttachingConnector().defaultArguments();
		Connector.IntegerArgument arg = (Connector.IntegerArgument)def.get("port"); //$NON-NLS-1$
		arg.setValue(8000);
		return def;
	}

	/**
	 * @see IVMConnector#getArgumentOrder()
	 */
	public List getArgumentOrder() {
		List list = new ArrayList(2);
		list.add("hostname"); //$NON-NLS-1$
		list.add("port"); //$NON-NLS-1$
		return list;
	}

}
