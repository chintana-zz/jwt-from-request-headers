# jwt-from-request-headers

## Purpose

This will create a custom JWT and include a list of user speficied HTTP header:value pairs that's in the request as claims in JWT. Useful in a scenario where you want to pass a set of headers to backend service and make sure they're not tampered with during transit. JWT will be signed by the same certificate configured for API Manager.  

## Usage

Create a mediation extension and configure mediator as below,

```
<class name="com.wso2.custom.jwt.JWTFromRequestHeaders">
    <property name="outgoingHeaderName" value="CustomJWT" />
    <property name="includeTheseHeaders" value="Username,Domain,MyHeader" />
    <property name="expirationTimeInSeconds" value="7200" />
</class>
```

Add a mediation extension using API Manager Tooling.

<img src="img/add-class-using-apim-tooling.png" />

Then engage this mediation extension for an API

<img src="img/enable-mediation-extension-for-api.png" />

Incoming request to API should have those custom headers.

```
GET /myapi/v1/*
Host: localhost:8243
User-Agent: curl/7.54.0
Username:johndoe
Domain:example.com
MyHeader:TestValue123
Authorization: Bearer ee9cae32-54bb-3c88-88d2-7425c7192770
```

API Manager will generate following HTTP header and attach it to outgoing request to backend service.

```
CustomJWT: eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJEb21haW4iOiJleGFtcGxlLmNvbSIsImV4cCI6MTUxMDQ0NTUxMywiVXNlcm5hbWUiOiJqb2huZG9lIiwiTXlIZWFkZXIiOiJUZXN0VmFsdWUxMjMifQ==.JMFaVK7IfshRoiLD8mrCQ1lmKXV8DXSxRCjFrOH1c7Uq6pdSPM3T4IoP4dccUl9gXjGc1S7T7VNJLO/0gek2F4kB7eQRHnt6CQwF8JaBpzKNFB/wKrOZT7ZzN1pLXwe94N8XCyKAAP/uYc808BP+H1rnkaFvg4AjodPFJsLE81g=
```

Decoded JWT

<img src="img/decoded-jwt.png" />

## Building from source

Checkout project

```
$ git clone https://github.com/chintana/jwt-from-request-headers.git
```

Build using maven

```
$ cd jwt-from-request-headers
$ mvn clean install
```

This builds an OSGi bundle that should be copied to &lt;APIM_HOME&gt;/repository/components/dropins. Copy JAR with underscores. 

```
$ cp target/jwt-from-request-headers-1.0-SNAPSHOT.jar ~/wso2am-2.1.0/repository/components/dropins/jwt_from_request_headers_1.0_SNAPSHOT.jar
```

Restart API Manager

## Troubleshooting

To enable debug logs add following line to &lt;APIM_HOME&gt;/repository/logs/log4j.properties

```
log4j.category.com.wso2.custom.jwt=DEBUG
```

Restart server, and you should see following set of logs in &lt;APIM_HOME&gt;/repository/logs/wso2carbon.log

```
[2017-11-12 11:23:31,812] DEBUG - JWTFromRequestHeaders Claims included in JWT
[2017-11-12 11:23:31,812] DEBUG - JWTFromRequestHeaders Username: johndoe
[2017-11-12 11:23:31,812] DEBUG - JWTFromRequestHeaders Domain: example.com
[2017-11-12 11:23:31,812] DEBUG - JWTFromRequestHeaders MyHeader: TestValue123
[2017-11-12 11:23:31,812] DEBUG - JWTFromRequestHeaders Trying to get keystore for tenant: -1234
[2017-11-12 11:23:31,814] DEBUG - JWTFromRequestHeaders JWT: eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJEb21haW4iOiJleGFtcGxlLmNvbSIsImV4cCI6MTUxMDUyMTgxMSwiVXNlcm5hbWUiOiJqb2huZG9lIiwiTXlIZWFkZXIiOiJUZXN0VmFsdWUxMjMifQ==.EtXIm+Ce1+UpwP3s/qGEzcDJbpP4DyfBzJt1mvxMmGSdu2rlXVjyxS4D+biyQXsiHo6hT1CEdSZR4opAqYBZzwEm7U3tHke4iU2MvQ9MqqC23+NfUEDx+N6geV55XXqdTDaOIHzr39SiMAHIVeJLOyhU9EsCLtP1WYD1tk+WpkI=
[2017-11-12 11:23:31,814] DEBUG - JWTFromRequestHeaders Attaching JWT to transport header: CustomJWT
```

You can further enable wirelogs by enabling following line in log4j.properties

```
log4j.logger.org.apache.synapse.transport.http.wire=DEBUG
```
