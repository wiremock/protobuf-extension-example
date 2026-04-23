package org.wiremock.protobuf;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.github.tomakehurst.wiremock.extension.WireMockServices;
import com.google.protobuf.Descriptors;
import java.util.List;
import org.wiremock.protobuf.internal.DescriptorLoader;
import org.wiremock.protobuf.internal.JsonMessageConverter;
import org.wiremock.protobuf.internal.ProtobufRequestFilter;
import org.wiremock.protobuf.internal.ProtobufResponseTransformer;

public class ProtobufExtensionFactory implements ExtensionFactory {

  private final String descriptorPath;

  public ProtobufExtensionFactory() {
    this("/protobuf/descriptor.desc");
  }

  public ProtobufExtensionFactory(String descriptorPath) {
    this.descriptorPath = descriptorPath;
  }

  @Override
  public List<Extension> create(WireMockServices services) {
    Descriptors.Descriptor messageDescriptor = DescriptorLoader.load(descriptorPath);
    JsonMessageConverter converter = new JsonMessageConverter();
    return List.of(
        new ProtobufRequestFilter(messageDescriptor, converter),
        new ProtobufResponseTransformer(messageDescriptor, converter));
  }
}
