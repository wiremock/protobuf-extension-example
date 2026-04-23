package org.wiremock.protobuf.internal;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class DescriptorLoader {

  public static Descriptors.Descriptor load(String classpathResource) {
    try (InputStream is = DescriptorLoader.class.getResourceAsStream(classpathResource)) {
      if (is == null) {
        throw new IllegalStateException(
            "Descriptor file not found on classpath: " + classpathResource);
      }

      DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(is);

      Map<String, Descriptors.FileDescriptor> resolved = new HashMap<>();
      Descriptors.Descriptor result = null;

      for (DescriptorProtos.FileDescriptorProto fdp : fds.getFileList()) {
        Descriptors.FileDescriptor[] deps =
            fdp.getDependencyList().stream()
                .map(resolved::get)
                .toArray(Descriptors.FileDescriptor[]::new);

        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(fdp, deps);
        resolved.put(fdp.getName(), fd);

        if (result == null
            && !fdp.getName().startsWith("google/")
            && fdp.getMessageTypeCount() > 0) {
          result = fd.getMessageTypes().get(0);
        }
      }

      if (result == null) {
        throw new IllegalStateException(
            "No user-defined message type found in descriptor: " + classpathResource);
      }
      return result;

    } catch (IOException | Descriptors.DescriptorValidationException e) {
      throw new RuntimeException("Failed to load protobuf descriptor: " + classpathResource, e);
    }
  }
}
