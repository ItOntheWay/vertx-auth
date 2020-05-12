package io.vertx.ext.auth.test.oauth2;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class OAuth2KeyRotationTest extends VertxTestBase {

  private static final JsonObject fixtureJwks = new JsonObject(
    "{\"keys\":" +
      "  [    " +
      "   {" +
      "    \"kty\":\"RSA\"," +
      "    \"n\": \"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\"," +
      "    \"e\":\"AQAB\"," +
      "    \"alg\":\"RS256\"," +
      "    \"kid\":\"1\"" +
      "   }" +
      "  ]" +
      "}");

  protected OAuth2Auth oauth2;
  private HttpServer server;
  private int connectionCounter;

  final AtomicInteger cnt = new AtomicInteger(0);
  final AtomicLong then = new AtomicLong();

  private Handler<HttpServerRequest> requestHandler;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    oauth2 = OAuth2Auth.create(vertx, new OAuth2ClientOptions()
      .setFlow(OAuth2FlowType.AUTH_CODE)
      .setClientID("client-id")
      .setClientSecret("client-secret")
      .setJwkPath("/oauth/jwks")
      .setSite("http://localhost:8080"));

    final CountDownLatch latch = new CountDownLatch(1);

    server = vertx.createHttpServer()
      .connectionHandler(c -> connectionCounter++)
      .requestHandler(req -> {
        if (req.method() == HttpMethod.GET && "/oauth/jwks".equals(req.path())) {
          req.bodyHandler(buffer -> {
            if (cnt.compareAndSet(0, 1)) {
              then.set(System.currentTimeMillis());
              req.response()
                .putHeader("Content-Type", "application/json")
                // we expect a refresh within 5 sec
                .putHeader("Cache-Control", "public, max-age=5, must-revalidate, no-transform")
                .end(fixtureJwks.encode());
              return;
            }
            if (cnt.compareAndSet(1, 2)) {
              requestHandler.handle(req);
            } else {
              fail("Too many calls on the mock");
            }
          });
        } else {
          req.response().setStatusCode(400).end();
        }
      })
      .listen(8080, ready -> {
        if (ready.failed()) {
          throw new RuntimeException(ready.cause());
        }
        // ready
        latch.countDown();
      });

    connectionCounter = 0;
    latch.await();
  }

  @Override
  public void tearDown() throws Exception {
    server.close();
    super.tearDown();
  }

  @Test
  public void testLoadJWK() {
    OAuth2Auth oauth2 = GoogleAuth.create(vertx, "", "");

    oauth2.loadJWK(load -> {
      assertFalse(load.failed());
      testComplete();
    });
    await();
  }

  @Test
  public void testAutoRefresh() {
    requestHandler = req -> {
      if (then.get() + 5000 <= System.currentTimeMillis()) {
        req.response()
          .putHeader("Content-Type", "application/json")
          .end(fixtureJwks.encode());
        // allow the process to complete
        vertx.runOnContext(n -> testComplete());
      } else {
        fail("wrong timing: " + (System.currentTimeMillis() - then.get()));
      }
    };

    oauth2.loadJWK(res -> {
      if (res.failed()) {
        fail(res.cause().getMessage());
      }
    });
    await();
  }

  @Test
  public void testMissingKey() {
    requestHandler = req -> {
      fail("Unexpected, missing key was not called");
    };

    String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjIifQ.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.NYY8FXsouaKSuMafoNshtQ997X4x1Jta0GEtl3BAJGY";

    oauth2.loadJWK(res -> {
      if (res.failed()) {
        fail(res.cause());
      } else {
        oauth2
          .missingKeyHandler(kid -> {
            if ("HS256#<null>".equals(kid)) {
              testComplete();
            } else {
              fail("wrong key id");
            }
          })
          .authenticate(new JsonObject().put("access_token", jwt), authenticate -> {
            if (authenticate.failed()) {
              // OK
            } else {
              fail("we don't have such key");
            }
          });
      }
    });
    await();
  }
}
