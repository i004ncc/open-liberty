/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
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
package com.ibm.ws.jaxrs20.client.ComplexClientTest.client;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

public class ClientResponseFilter1 implements ClientResponseFilter {

    @Override
    public void filter(final ClientRequestContext reqCtx,
                       final ClientResponseContext resCtx) throws IOException {
        if (resCtx.getDate() != null) {
            resCtx.setStatus(222);
        }
        System.out.println("ClientResponseFilter1: ");
        System.out.println("status: " + resCtx.getStatus());
        System.out.println("date: " + resCtx.getDate());
        System.out.println("last-modified: " + resCtx.getLastModified());
    }
}
