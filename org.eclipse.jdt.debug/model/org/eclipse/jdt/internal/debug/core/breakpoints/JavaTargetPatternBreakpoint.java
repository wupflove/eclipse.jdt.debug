package org.eclipse.jdt.internal.debug.core.breakpoints;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
 
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaPatternBreakpoint;
import org.eclipse.jdt.debug.core.IJavaTargetPatternBreakpoint;
import org.eclipse.jdt.internal.debug.core.JDIDebugPlugin;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.core.model.JDIType;
import org.eclipse.jdt.internal.debug.core.model.JDIValue;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.EventRequest;

public class JavaTargetPatternBreakpoint extends JavaLineBreakpoint implements IJavaTargetPatternBreakpoint {

	private static final String TARGET_PATTERN_BREAKPOINT = "org.eclipse.jdt.debug.javaTargetPatternBreakpointMarker"; //$NON-NLS-1$	
	
	/**
	 * Table of targets to patterns
	 */
	private HashMap fPatterns;
	
	public JavaTargetPatternBreakpoint() {
	}
	
	/**
	 * @see JDIDebugModel#createPatternBreakpoint(IResource, String, int, int, int, int, boolean, Map)
	 */	
	public JavaTargetPatternBreakpoint(IResource resource, String sourceName, int lineNumber, int charStart, int charEnd, int hitCount, boolean add, Map attributes) throws DebugException {
		this(resource, sourceName, lineNumber, charStart, charEnd, hitCount, add, attributes, TARGET_PATTERN_BREAKPOINT);
	}
	
	public JavaTargetPatternBreakpoint(final IResource resource, final String sourceName, final int lineNumber, final int charStart, final int charEnd, final int hitCount, final boolean add, final Map attributes, final String markerType) throws DebugException {
		IWorkspaceRunnable wr= new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
	
				// create the marker
				setMarker(resource.createMarker(markerType));
				
				// add attributes
				addLineBreakpointAttributes(attributes, getModelIdentifier(), true, lineNumber, charStart, charEnd);
				addSourceNameAndHitCount(attributes, sourceName, hitCount);
				
				// set attributes
				ensureMarker().setAttributes(attributes);
				
				register(add);
			}
		};
		run(wr);
	}
	
	/**
	 * Creates the event requests to:<ul>
	 * <li>Listen to class loads related to the breakpoint</li>
	 * <li>Respond to the breakpoint being hit</li>
	 * </ul>
	 */
	public void addToTarget(JDIDebugTarget target) throws CoreException {
		
		// pre-notification
		fireAdding(target);
				
		String referenceTypeName= getPattern(target);
		if (referenceTypeName == null) {
			return;
		}
		
		String classPrepareTypeName= referenceTypeName;
		// create request to listen to class loads
		//name may only be partially resolved
		if (!referenceTypeName.endsWith("*")) { //$NON-NLS-1$
			classPrepareTypeName= classPrepareTypeName + '*';
		}
		registerRequest(target.createClassPrepareRequest(classPrepareTypeName), target);
		
		// create breakpoint requests for each class currently loaded
		List classes= target.getVM().allClasses();
		if (classes != null) {
			Iterator iter = classes.iterator();
			String typeName= null;
			ReferenceType type= null;
			while (iter.hasNext()) {
				type= (ReferenceType) iter.next();
				typeName= type.name();
				if (typeName != null && typeName.startsWith(referenceTypeName)) {
					createRequest(target, type);
				}
			}
		}
	}	
	
	/**
	 * @see JavaBreakpoint#getReferenceTypeName()
	 */
	protected String getReferenceTypeName() {
		String name= "*"; //$NON-NLS-1$
		try {
			name= getSourceName();
		} catch (CoreException ce) {
			JDIDebugPlugin.log(ce);
		}
		return name;
	}
	
	/**
	 * @see JavaBreakpoint#installableReferenceType(ReferenceType)
	 */
	protected boolean installableReferenceType(ReferenceType type, JDIDebugTarget target) throws CoreException {
		// if the source name attribute is specified, check for a match with the
		// debug attribute (if available)
		if (getSourceName() != null) {
			String sourceName = null;
			try {
				sourceName = type.sourceName();
			} catch (AbsentInformationException e) {
				// unable to compare
			} catch (RuntimeException e) {
				target.targetRequestFailed(MessageFormat.format(JDIDebugBreakpointMessages.getString("JavaPatternBreakpoint.exception_source_name"),new String[] {e.toString(), type.name()}) ,e); //$NON-NLS-1$
				// execution will not reach this line, as 
				// #targetRequestFailed will throw an exception			
				return false;
			}
			
			// if the debug attribute matches the source name, attempt installion
			if (sourceName != null) {
				if (!getSourceName().equalsIgnoreCase(sourceName)) {
					return false;
				}
			}
		}
		
		String pattern= getPattern(target);
		String queriedType= type.name();
		if (pattern == null || queriedType == null) {
			return false;
		}
		if (queriedType.startsWith(pattern)) {
			// query registered listeners to see if this pattern breakpoint should
			// be installed in the given target
			return queryInstallListeners(target, type);
		}
		return false;
	}	
	
	/**
	 * Adds the source name and hit count attributes to the gvien map.
	 */
	protected void addSourceNameAndHitCount(Map attributes, String sourceName, int hitCount) throws CoreException {
		if (sourceName != null) {
			attributes.put(SOURCE_NAME, sourceName);
		}		
		if (hitCount > 0) {
			attributes.put(HIT_COUNT, new Integer(hitCount));
			attributes.put(EXPIRED, new Boolean(false));
		}
	}
	
	/**
	 * @see IJavaTargetPatternBreakpoint#getPattern(IJavaDebugTarget)
	 */
	public String getPattern(IJavaDebugTarget target) {
		if (fPatterns != null) {
			return (String)fPatterns.get(target);
		}
		return null;
	}	
	
	/**
	 * @see IJavaTargetPatternBreakpoint#setPattern(IJavaDebugTarget, String)
	 */
	public void setPattern(IJavaDebugTarget target, String pattern) throws CoreException {
		if (fPatterns == null) {
			fPatterns = new HashMap(2);
		}
		// if pattern is changing then remove and re-add
		String oldPattern = getPattern(target);
		fPatterns.put(target, pattern);
		if (oldPattern != null && !oldPattern.equals(pattern)) {
			removeFromTarget((JDIDebugTarget)target);
			addToTarget((JDIDebugTarget)target);
		}
	}		
	
	/**
	 * @see IJavaPatternBreakpoint#getSourceName()
	 */
	public String getSourceName() throws CoreException {
		return (String) ensureMarker().getAttribute(SOURCE_NAME);		
	}		

}

