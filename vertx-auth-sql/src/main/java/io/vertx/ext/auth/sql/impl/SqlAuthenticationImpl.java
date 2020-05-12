/*
 * Copyright 2014 Red Hat, Inc.
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

package io.vertx.ext.auth.sql.impl;

import java.util.Map;
import java.util.Objects;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.HashingStrategy;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.InvalidAuthInfoException;
import io.vertx.ext.auth.sql.SqlAuthentication;
import io.vertx.ext.auth.sql.SqlAuthenticationOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import static io.vertx.ext.auth.impl.AuthInfoUtil.getNonEmpty;
import static io.vertx.ext.auth.impl.AuthInfoUtil.getNonNull;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SqlAuthenticationImpl implements SqlAuthentication {

  private final SqlClient client;
  private final SqlAuthenticationOptions options;
  private final HashingStrategy strategy = HashingStrategy.load();

  public SqlAuthenticationImpl(SqlClient client, SqlAuthenticationOptions options) {
    this.client = Objects.requireNonNull(client);
    this.options = Objects.requireNonNull(options);
  }

  @Override
  public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
    try {
      String username = getNonEmpty(authInfo, "username");
      String password = getNonNull(authInfo, "password");

      client.preparedQuery(options.getAuthenticationQuery()).execute(Tuple.of(username), preparedQuery -> {
        if (preparedQuery.succeeded()) {
          final RowSet<Row> rows = preparedQuery.result();
          switch (rows.size()) {
            case 0: {
              // Unknown user/password
              resultHandler.handle(Future.failedFuture("Invalid username/password"));
              break;
            }
            case 1: {
              Row row = rows.iterator().next();
              String hashedStoredPwd = row.getString(0);
              if (strategy.verify(hashedStoredPwd, password)) {
                resultHandler.handle(Future.succeededFuture(User.create(new JsonObject().put("username", username))));
              } else {
                resultHandler.handle(Future.failedFuture("Invalid username/password"));
              }
              break;
            }
            default: {
              // More than one row returned!
              resultHandler.handle(Future.failedFuture("Failure in authentication"));
              break;
            }
          }
        } else {
          resultHandler.handle(Future.failedFuture(preparedQuery.cause()));
        }
      });
    } catch (InvalidAuthInfoException e) {
      resultHandler.handle(Future.failedFuture(e));
    }
  }

  @Override
  public String hash(String id, Map<String, String> params, String salt, String password) {
    return strategy.hash(id, params, salt, password);
  }
}
