# WireMock Protobuf Extension

A [WireMock](https://wiremock.org/) extension that adds Protocol Buffers support, allowing you to stub and verify protobuf-encoded HTTP APIs using familiar JSON matching.

## How it works

The extension automatically:

1. **Decodes** incoming protobuf request bodies into JSON so you can use WireMock's standard JSON matchers (`equalToJson`, `matchingJsonPath`, etc.)
2. **Encodes** JSON response bodies back into protobuf wire format when the request has a protobuf content type

Requests with `Content-Type: application/x-protobuf` or `application/protobuf` are processed. All other requests pass through unmodified.

## Prerequisites

- Java 17+
- WireMock 3.x

## Building

```bash
./gradlew shadowJar
```

The shadow JAR at `build/libs/wiremock-protobuf-extension-<version>-all.jar` includes protobuf and all its transitive dependencies, shaded to avoid classpath conflicts.

## Usage

### Adding the extension

Place the shadow JAR on WireMock's classpath. The extension is loaded automatically via `ExtensionFactory` service loading.

Or register it programmatically:

```java
WireMockServer wm = new WireMockServer(
    wireMockConfig()
        .extensions(new ProtobufExtensionFactory())
);
```

### Defining your protobuf schema

Place your `.proto` files in `src/main/proto`. The build generates a descriptor set at `protobuf/descriptor.desc` which the extension uses at runtime to dynamically parse messages.

### Stubbing

Define stubs with JSON bodies as normal -- the extension handles the protobuf encoding:

```java
stubFor(
    post(urlPathEqualTo("/api/things"))
        .withRequestBody(equalToJson("{ \"name\": \"Widget\", \"quantity\": 3 }"))
        .willReturn(ok().withBody("{ \"id\": \"123\", \"name\": \"Widget\", \"quantity\": 5 }"))
);
```

### Verification

Verify requests using JSON matchers against the decoded protobuf body:

```java
verify(
    postRequestedFor(urlPathEqualTo("/api/things"))
        .withRequestBody(matchingJsonPath("$.name", equalTo("Widget")))
);
```

### Response templating

The extension works with WireMock's response templating:

```java
stubFor(
    post(urlPathEqualTo("/api/things"))
        .willReturn(ok()
            .withBody("{ \"id\": \"789\", \"name\": \"{{jsonPath request.body '$.name'}}\" }")
            .withTransformers("response-template"))
);
```

## Custom descriptor path

By default the extension loads the descriptor from `/protobuf/descriptor.desc` on the classpath. To use a different path:

```java
new ProtobufExtensionFactory("/custom/path/descriptor.desc")
```

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
