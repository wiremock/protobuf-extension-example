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

public class ProtobufAcceptanceTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().extensions(new ProtobufExtensionFactory()))
          .build();

  static final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void decodesProtobufRequestAndEncodesProtobufResponse() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(
                ok().withBody("{ \"id\": \"123\", \"name\": \"Widget\", \"quantity\": 5 }")));

    Thing request = Thing.newBuilder().setName("Widget").setQuantity(3).build();

    HttpResponse<byte[]> response = sendProtobufRequest("/api/things", request);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/x-protobuf");

    Thing responseThing = Thing.parseFrom(response.body());
    assertThat(responseThing.getId()).isEqualTo("123");
    assertThat(responseThing.getName()).isEqualTo("Widget");
    assertThat(responseThing.getQuantity()).isEqualTo(5);
  }

  @Test
  void matchesDecodedRequestBodyAsJson() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/things"))
            .withRequestBody(equalToJson("{ \"name\": \"Gadget\", \"quantity\": 10 }"))
            .willReturn(ok().withBody("{ \"id\": \"456\", \"name\": \"Gadget\" }")));

    Thing matching = Thing.newBuilder().setName("Gadget").setQuantity(10).build();
    HttpResponse<byte[]> matchResponse = sendProtobufRequest("/api/things", matching);
    assertThat(matchResponse.statusCode()).isEqualTo(200);

    Thing nonMatching = Thing.newBuilder().setName("Other").setQuantity(1).build();
    HttpResponse<byte[]> noMatchResponse = sendProtobufRequest("/api/things", nonMatching);
    assertThat(noMatchResponse.statusCode()).isEqualTo(404);
  }

  @Test
  void supportsResponseTemplating() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(
                ok().withBody(
                        "{ \"id\": \"789\", \"name\": \"{{jsonPath request.body '$.name'}}\" }")
                    .withTransformers("response-template")));

    Thing request = Thing.newBuilder().setName("Gizmo").build();
    HttpResponse<byte[]> response = sendProtobufRequest("/api/things", request);

    assertThat(response.statusCode()).isEqualTo(200);
    Thing responseThing = Thing.parseFrom(response.body());
    assertThat(responseThing.getName()).isEqualTo("Gizmo");
  }

  @Test
  void nonProtobufRequestsPassThrough() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/json"))
            .withRequestBody(equalToJson("{ \"key\": \"value\" }"))
            .willReturn(okJson("{ \"result\": \"ok\" }")));

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(wm.url("/api/json")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{ \"key\": \"value\" }"))
            .build();

    HttpResponse<String> response =
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"result\"");
  }

  @Test
  void handlesRepeatedFields() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(
                ok().withBody(
                    "{ \"id\": \"t1\", \"name\": \"Tagged\", \"tags\": [\"a\", \"b\", \"c\"] }")));

    Thing request = Thing.newBuilder().setName("Tagged").build();
    HttpResponse<byte[]> response = sendProtobufRequest("/api/things", request);

    Thing responseThing = Thing.parseFrom(response.body());
    assertThat(responseThing.getTagsList()).containsExactly("a", "b", "c");
  }

  @Test
  void supportsApplicationProtobufContentType() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/things"))
            .willReturn(ok().withBody("{ \"id\": \"1\", \"name\": \"Alt\" }")));

    Thing request = Thing.newBuilder().setName("Alt").build();

    HttpRequest httpRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(wm.url("/api/things")))
            .header("Content-Type", "application/protobuf")
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.toByteArray()))
            .build();

    HttpResponse<byte[]> response =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

    assertThat(response.statusCode()).isEqualTo(200);
    Thing responseThing = Thing.parseFrom(response.body());
    assertThat(responseThing.getName()).isEqualTo("Alt");
  }

  @Test
  void verifiesProtobufRequests() throws Exception {
    wm.stubFor(
        post(urlPathEqualTo("/api/things")).willReturn(ok().withBody("{ \"name\": \"Any\" }")));

    Thing request = Thing.newBuilder().setName("Verified").setQuantity(7).build();
    sendProtobufRequest("/api/things", request);

    wm.verify(
        postRequestedFor(urlPathEqualTo("/api/things"))
            .withRequestBody(matchingJsonPath("$.name", equalTo("Verified"))));
  }

  private HttpResponse<byte[]> sendProtobufRequest(String path, Thing message) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(wm.url(path)))
            .header("Content-Type", "application/x-protobuf")
            .POST(HttpRequest.BodyPublishers.ofByteArray(message.toByteArray()))
            .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
  }
}
