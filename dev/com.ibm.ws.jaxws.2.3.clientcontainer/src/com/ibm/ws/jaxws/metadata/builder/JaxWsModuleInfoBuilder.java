/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
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
package com.ibm.ws.jaxws.metadata.builder;

import com.ibm.ws.jaxws.metadata.JaxWsModuleInfo;
import com.ibm.ws.jaxws.metadata.JaxWsModuleType;
import com.ibm.ws.runtime.metadata.ModuleMetaData;
import com.ibm.wsspi.adaptable.module.Container;
import com.ibm.wsspi.adaptable.module.UnableToAdaptException;

/**
 * The implementations of this interface will analysis the current module and add EndpointInfo in the JaxWsModuleInfo instance
 */
public interface JaxWsModuleInfoBuilder {

    public void build(ModuleMetaData moduleMetaData, Container containerToAdapt, JaxWsModuleInfo jaxWsModuleInfo) throws UnableToAdaptException;

    public JaxWsModuleType getSupportType();

}
