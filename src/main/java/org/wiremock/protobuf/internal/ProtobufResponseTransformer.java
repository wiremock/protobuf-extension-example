package org.wiremock.protobuf.internal;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import java.util.ArrayList;
import java.util.List;

public class ProtobufResponseTransformer implements ResponseTransformerV2 {

  private final Descriptors.Descriptor messageDescriptor;
  private final JsonMessageConverter converter;

  public ProtobufResponseTransformer(
      Descriptors.Descriptor messageDescriptor, JsonMessageConverter converter) {
    this.messageDescriptor = messageDescriptor;
    this.converter = converter;
  }

  @Override
  public Response transform(Response response, ServeEvent serveEvent) {
    if (!ProtobufRequestFilter.isProtobufRequest(serveEvent.getRequest())) {
      return response;
    }

    if (isProtobufResponse(response)) {
      return addOriginalContentTypeHeader(response);
    }

    String body = response.getBodyAsString();
    if (body == null || body.isEmpty()) {
      return response;
    }

    try {
      DynamicMessage message = converter.toMessage(body, messageDescriptor);
      byte[] protobufBytes = message.toByteArray();

      List<HttpHeader> headers = new ArrayList<>();
      if (response.getHeaders() != null) {
        for (HttpHeader header : response.getHeaders().all()) {
          if (!header.key().equalsIgnoreCase("Content-Type")) {
            headers.add(header);
          }
        }
      }
      headers.add(new HttpHeader("Content-Type", "application/x-protobuf"));

      return Response.response()
          .status(response.getStatus())
          .headers(new HttpHeaders(headers))
          .body(protobufBytes)
          .build();
    } catch (Exception e) {
      return response;
    }
  }

  private static boolean isProtobufResponse(Response response) {
    if (response.getHeaders() == null) {
      return false;
    }
    HttpHeader contentType = response.getHeaders().getHeader("Content-Type");
    if (contentType == null || !contentType.isPresent()) {
      return false;
    }
    String value = contentType.firstValue();
    return value.contains("application/x-protobuf") || value.contains("application/protobuf");
  }

  private static Response addOriginalContentTypeHeader(Response response) {
    String originalContentType = response.getHeaders().getHeader("Content-Type").firstValue();
    List<HttpHeader> headers = new ArrayList<>(response.getHeaders().all());
    headers.add(new HttpHeader("X-Original-Content-Type", originalContentType));

    return Response.response()
        .status(response.getStatus())
        .headers(new HttpHeaders(headers))
        .body(response.getBody())
        .build();
  }

  @Override
  public String getName() {
    return "protobuf-response-transformer";
  }

  @Override
  public boolean applyGlobally() {
    return true;
  }
}
