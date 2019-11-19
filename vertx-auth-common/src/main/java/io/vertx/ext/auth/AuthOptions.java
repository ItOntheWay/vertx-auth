/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.auth;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.Vertx;

/**
 * A common base object for authentication options.<p>
 *
 * @deprecated do not use this interface to create a provider, use the provider specific factory.
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@Deprecated
public interface AuthOptions {

  AuthOptions clone();

  /**
   * Create the suitable provider for this option.
   *
   * @param vertx the vertx instance
   * @return the auth provider
   */
  AuthProvider createProvider(Vertx vertx);
}
