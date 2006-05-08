/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.debug.core.refactoring;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils;
import org.eclipse.ltk.core.refactoring.Change;

/**
 * @since 3.2
 *
 */
public class MethodBreakpointTypeChange extends MethodBreakpointChange {

	private IType fDestType;
	
	public MethodBreakpointTypeChange(IJavaMethodBreakpoint breakpoint, IType destType) throws CoreException {
		super(breakpoint);
		fDestType = destType;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#getName()
	 */
	public String getName() {
		return RefactoringMessages.MethodBreakpointTypeChange_0;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#perform(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public Change perform(IProgressMonitor pm) throws CoreException {
		IMethod destMethod = fDestType.getMethod(getMethodName(), Signature.getParameterTypes(getSignature()));
		Map map = new HashMap();
		BreakpointUtils.addJavaBreakpointAttributes(map, destMethod);
		IResource resource = BreakpointUtils.getBreakpointResource(destMethod);
		int[] range = getNewLineNumberAndRange(destMethod);
		IJavaMethodBreakpoint breakpoint = JDIDebugModel.createMethodBreakpoint(
				resource,
				fDestType.getFullyQualifiedName(),
				getMethodName(),
				getSignature(),
				isEntry(),
				isExit(),
				isNativeOnly(),
				range[0],
				range[1],
				range[2],
				getHitCount(), 
				true,
				map);
		apply(breakpoint);
		getOriginalBreakpoint().delete();
		return new DeleteBreakpointChange(breakpoint);
	}

}
