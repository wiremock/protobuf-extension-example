package org.wiremock.protobuf.internal;

import com.github.tomakehurst.wiremock.extension.RecorderServeEventTransformer;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProtobufRecorderServeEventTransformer implements RecorderServeEventTransformer {

  private final Descriptors.Descriptor messageDescriptor;
  private final JsonMessageConverter converter;

  public ProtobufRecorderServeEventTransformer() {
    this.messageDescriptor = DescriptorLoader.load("/protobuf/descriptor.desc");
    this.converter = new JsonMessageConverter();
  }

  @Override
  public ServeEvent transform(ServeEvent serveEvent) {
    ServeEvent result = serveEvent;

    if (isProtobufContentType(serveEvent.getRequest().contentTypeHeader())) {
      try {
        byte[] body = serveEvent.getRequest().getBody();
        String json = tryDecodeAsJson(body);

        List<HttpHeader> requestHeaders = replaceContentType(
            serveEvent.getRequest().getHeaders(), "application/json");
        result = result.withRequest(
            serveEvent.getRequest().transform(builder ->
                builder.withBody(json.getBytes(StandardCharsets.UTF_8))
                    .withHeaders(new HttpHeaders(requestHeaders))));
      } catch (Exception e) {
        // leave request unchanged
      }
    }

    if (result.getResponse() != null
        && isProtobufContentType(result.getResponse().getHeaders())) {
      try {
        byte[] body = result.getResponse().getBody();
        String json = tryDecodeAsJson(body);

        String originalContentType = getContentType(result.getResponse().getHeaders());
        List<HttpHeader> responseHeaders = replaceContentType(
            result.getResponse().getHeaders(), "application/json");
        responseHeaders.add(new HttpHeader("X-Original-Content-Type", originalContentType));

        result = result.withResponse(
            result.getResponse().transform(builder ->
                builder.withBody(json.getBytes(StandardCharsets.UTF_8))
                    .withHeaders(new HttpHeaders(responseHeaders))));
      } catch (Exception e) {
        // leave response unchanged
      }
    }

    return result;
  }

  @Override
  public String getName() {
    return "protobuf-recorder-serve-event-transformer";
  }

  private static boolean isProtobufContentType(ContentTypeHeader contentType) {
    if (contentType == null || !contentType.isPresent()) {
      return false;
    }
    String mimeType = contentType.mimeTypePart();
    return "application/x-protobuf".equals(mimeType) || "application/protobuf".equals(mimeType);
  }

  private static boolean isProtobufContentType(HttpHeaders headers) {
    if (headers == null) {
      return false;
    }
    HttpHeader header = headers.getHeader("Content-Type");
    if (header == null || !header.isPresent()) {
      return false;
    }
    String value = header.firstValue();
    return value.contains("application/x-protobuf") || value.contains("application/protobuf");
  }

  private static String getContentType(HttpHeaders headers) {
    HttpHeader header = headers.getHeader("Content-Type");
    return header.firstValue();
  }

  private String tryDecodeAsJson(byte[] body) throws Exception {
    String bodyString = new String(body, StandardCharsets.UTF_8);
    String trimmed = bodyString.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      return bodyString;
    }
    DynamicMessage message = DynamicMessage.parseFrom(messageDescriptor, body);
    return converter.toJson(message);
  }

  private static List<HttpHeader> replaceContentType(HttpHeaders headers, String newContentType) {
    List<HttpHeader> result = new ArrayList<>();
    if (headers != null) {
      for (HttpHeader header : headers.all()) {
        if (!header.key().equalsIgnoreCase("Content-Type")) {
          result.add(header);
        }
      }
    }
    result.add(new HttpHeader("Content-Type", newContentType));
    return result;
  }
}
