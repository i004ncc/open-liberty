/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
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
package web;

import com.ibm.ws.threading.PolicyTaskCallback;
import com.ibm.ws.threading.PolicyTaskFuture;

/**
 * Callback that cancels tasks on submit or on start.
 */
public class CancellationCallback extends PolicyTaskCallback {
    private boolean interrupt;
    private String whenToCancel;

    /**
     * @param whenToCancel supported values: onSubmit, onStart
     * @param interrupt whether or not to cancel with interrupt
     */
    public CancellationCallback(String whenToCancel, boolean interrupt) {
        this.whenToCancel = whenToCancel;
        this.interrupt = interrupt;
    }

    @Override
    public Object onStart(Object task, PolicyTaskFuture<?> future) {
        if ("onStart".equals(whenToCancel))
            System.out.println("CancellationCallback.onStart " + task.toString() + " canceled? " + future.cancel(interrupt));
        return null;
    }

    @Override
    public void onSubmit(Object task, PolicyTaskFuture<?> future, int invokeAnyCount) {
        if ("onSubmit".equals(whenToCancel))
            System.out.println("CancellationCallback.onSubmit " + task.toString() + " canceled? " + future.cancel(interrupt));
    }
}
