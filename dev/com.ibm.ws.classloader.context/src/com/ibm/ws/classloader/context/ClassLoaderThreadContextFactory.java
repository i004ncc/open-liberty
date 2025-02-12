/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.classloader.context;

import java.util.Map;

import com.ibm.wsspi.threadcontext.ThreadContext;

/**
 * Interface for creating thread context that can be captured and applied to other threads.
 */
public interface ClassLoaderThreadContextFactory {

    /**
     * Creates thread context for the given classloader.
     * 
     * The value returned must be a new instance if the thread context implementation stores any state information
     * (for example, previous thread context to restore after a contextual task ends).
     * 
     * @param execProps execution properties that provide information about the contextual task.
     * @param classloaderIdentifier identifies the classloader
     * 
     * @return context that can be applied to a thread.
     * 
     * @see javax.enterprise.concurrent.ManagedTask#getExecutionProperties()
     */
    ThreadContext createThreadContext(Map<String, String> execProps, String classloaderIdentifier);

}
