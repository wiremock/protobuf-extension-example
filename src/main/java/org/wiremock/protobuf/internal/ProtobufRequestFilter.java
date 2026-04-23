package org.wiremock.protobuf.internal;

import com.github.tomakehurst.wiremock.common.Encoding;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ProtobufRequestFilter implements StubRequestFilterV2 {

  private final Descriptors.Descriptor messageDescriptor;
  private final JsonMessageConverter converter;

  public ProtobufRequestFilter(
      Descriptors.Descriptor messageDescriptor, JsonMessageConverter converter) {
    this.messageDescriptor = messageDescriptor;
    this.converter = converter;
  }

  @Override
  public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
    if (!isProtobufRequest(request)) {
      return RequestFilterAction.continueWith(request);
    }

    try {
      DynamicMessage message = DynamicMessage.parseFrom(messageDescriptor, request.getBody());
      String json = converter.toJson(message);
      return RequestFilterAction.continueWith(new DecodedRequest(request, json));
    } catch (Exception e) {
      return RequestFilterAction.continueWith(request);
    }
  }

  @Override
  public String getName() {
    return "protobuf-request-filter";
  }

  static boolean isProtobufRequest(Request request) {
    ContentTypeHeader contentType = request.contentTypeHeader();
    if (contentType == null || !contentType.isPresent()) {
      return false;
    }
    String mimeType = contentType.mimeTypePart();
    return "application/x-protobuf".equals(mimeType) || "application/protobuf".equals(mimeType);
  }

  private static class DecodedRequest implements Request {

    private final Request delegate;
    private final byte[] jsonBody;
    private final String jsonBodyString;

    DecodedRequest(Request delegate, String jsonBody) {
      this.delegate = delegate;
      this.jsonBodyString = jsonBody;
      this.jsonBody = jsonBody.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getBody() {
      return jsonBody;
    }

    @Override
    public String getBodyAsString() {
      return jsonBodyString;
    }

    @Override
    public String getBodyAsBase64() {
      return Encoding.encodeBase64(jsonBody);
    }

    // All other methods delegate to the original request

    @Override
    public String getUrl() {
      return delegate.getUrl();
    }

    @Override
    public String getAbsoluteUrl() {
      return delegate.getAbsoluteUrl();
    }

    @Override
    public RequestMethod getMethod() {
      return delegate.getMethod();
    }

    @Override
    public String getScheme() {
      return delegate.getScheme();
    }

    @Override
    public String getHost() {
      return delegate.getHost();
    }

    @Override
    public int getPort() {
      return delegate.getPort();
    }

    @Override
    public String getClientIp() {
      return delegate.getClientIp();
    }

    @Override
    public String getHeader(String key) {
      return delegate.getHeader(key);
    }

    @Override
    public HttpHeader header(String key) {
      return delegate.header(key);
    }

    @Override
    public ContentTypeHeader contentTypeHeader() {
      return delegate.contentTypeHeader();
    }

    @Override
    public HttpHeaders getHeaders() {
      return delegate.getHeaders();
    }

    @Override
    public boolean containsHeader(String key) {
      return delegate.containsHeader(key);
    }

    @Override
    public Set<String> getAllHeaderKeys() {
      return delegate.getAllHeaderKeys();
    }

    @Override
    public QueryParameter queryParameter(String key) {
      return delegate.queryParameter(key);
    }

    @Override
    public FormParameter formParameter(String key) {
      return delegate.formParameter(key);
    }

    @Override
    public Map<String, FormParameter> formParameters() {
      return delegate.formParameters();
    }

    @Override
    public Map<String, Cookie> getCookies() {
      return delegate.getCookies();
    }

    @Override
    public boolean isMultipart() {
      return delegate.isMultipart();
    }

    @Override
    public Collection<Part> getParts() {
      return delegate.getParts();
    }

    @Override
    public Part getPart(String name) {
      return delegate.getPart(name);
    }

    @Override
    public boolean isBrowserProxyRequest() {
      return delegate.isBrowserProxyRequest();
    }

    @Override
    public Optional<Request> getOriginalRequest() {
      return Optional.of(delegate);
    }

    @Override
    public String getProtocol() {
      return delegate.getProtocol();
    }
  }
}
