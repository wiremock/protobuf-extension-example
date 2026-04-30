package org.wiremock.protobuf;

import com.example.protobuf.Messages.Thing;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.wiremock.protobuf.internal.ProtobufRecorderServeEventTransformer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.http.RequestMethod.POST;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

public class ProtobufRecordTest {

  @RegisterExtension
  static WireMockExtension target =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort())
          .build();

  @RegisterExtension
  static WireMockExtension proxy =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .dynamicPort()
                  .extensions(new ProtobufRecorderServeEventTransformer()))
          .build();

  static final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void recordsProtobufExchangeAsPlainJson() throws Exception {
    target.stubFor(
        post(urlPathEqualTo("/api/things"))
            .withHeader("Content-Type", containing("application/x-protobuf"))
            .willReturn(
                ok().withBody(
                        Thing.newBuilder()
                            .setId("123")
                            .setName("Widget")
                            .setQuantity(5)
                            .build()
                            .toByteArray())
                    .withHeader("Content-Type", "application/x-protobuf")));

    proxy.startRecording(recordSpec()
            .makeStubsPersistent(false)
            .forTarget(target.baseUrl())
    );

    Thing request = Thing.newBuilder().setName("Widget").setQuantity(3).build();
    HttpResponse<byte[]> response = sendProtobufRequest("/api/things", request);
    proxy.stopRecording();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/x-protobuf");

    StubMapping recordedStub = proxy.listAllStubMappings().getMappings().stream()
            .filter(stub -> stub.getRequest().getUrl().equals("/api/things"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Stub /api/things not found"));

    RequestPattern recordedRequest = recordedStub.getRequest();
    assertThat(recordedRequest.getUrl()).isEqualTo("/api/things");
    assertThat(recordedRequest.getMethod()).isEqualTo(POST);
    assertThat(recordedRequest.getBodyPatterns()).isNotEmpty();
    assertThatJson(recordedRequest.getBodyPatterns().get(0).getValue().toString())
        .isObject()
        .containsEntry("name", "Widget")
        .containsEntry("quantity", 3);

    ResponseDefinition recordedResponse = recordedStub.getResponse();
    assertThat(recordedResponse.getHeaders().getHeader("Content-Type").firstValue()).isEqualTo("application/json");
    assertThatJson(recordedResponse.getBody())
        .isObject()
        .containsEntry("id", "123")
        .containsEntry("name", "Widget")
        .containsEntry("quantity", 5);
  }

  private HttpResponse<byte[]> sendProtobufRequest(String path, Thing message) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(proxy.url(path)))
            .header("Content-Type", "application/x-protobuf")
            .POST(HttpRequest.BodyPublishers.ofByteArray(message.toByteArray()))
            .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }
}
