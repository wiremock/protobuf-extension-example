package org.wiremock.protobuf;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.protobuf.Messages.Thing;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ProtobufProxyTest {

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
                  .extensions(new ProtobufExtensionFactory()))
          .build();

  static final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void proxiesProtobufRequestAndDecodesProtobufResponse() throws Exception {
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

    proxy.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(aResponse().proxiedFrom(target.baseUrl())));

    Thing request = Thing.newBuilder().setName("Widget").setQuantity(3).build();
    HttpResponse<byte[]> response = sendProtobufRequest("/api/things", request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/x-protobuf");

    Thing responseThing = Thing.parseFrom(response.body());
    assertThat(responseThing.getId()).isEqualTo("123");
    assertThat(responseThing.getName()).isEqualTo("Widget");
    assertThat(responseThing.getQuantity()).isEqualTo(5);

    // Verify the proxy converted the request back to protobuf for the target
    target.verify(
        postRequestedFor(urlPathEqualTo("/api/things"))
            .withHeader("Content-Type", containing("application/x-protobuf")));
  }

  @Test
  void proxiedProtobufResponseIsRecordedAsJson() throws Exception {
    target.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(
                ok().withBody(
                        Thing.newBuilder()
                            .setId("456")
                            .setName("Gadget")
                            .setQuantity(10)
                            .build()
                            .toByteArray())
                    .withHeader("Content-Type", "application/x-protobuf")));

    proxy.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(aResponse().proxiedFrom(target.baseUrl())));

    Thing request = Thing.newBuilder().setName("Gadget").build();
    sendProtobufRequest("/api/things", request);

    // The serve event should have the response body decoded to JSON
    // (the X-Original-Content-Type header indicates this happened)
    var serveEvents = proxy.getAllServeEvents();
    assertThat(serveEvents).hasSize(1);

    var proxyResponse = serveEvents.get(0).getResponse();
    assertThat(proxyResponse.getHeaders().getHeader("X-Original-Content-Type").firstValue())
        .isEqualTo("application/x-protobuf");
  }

  @Test
  void nonProtobufProxyRequestsPassThrough() throws Exception {
    target.stubFor(
        post(urlPathEqualTo("/api/json"))
            .willReturn(okJson("{ \"result\": \"ok\" }")));

    proxy.stubFor(
        post(urlPathEqualTo("/api/json"))
            .willReturn(aResponse().proxiedFrom(target.baseUrl())));

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(proxy.url("/api/json")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{ \"key\": \"value\" }"))
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"result\"");
    assertThat(response.headers().firstValue("X-Original-Content-Type")).isEmpty();
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
