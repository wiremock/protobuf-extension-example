package org.wiremock.protobuf.internal;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

public class JsonMessageConverter {

  private final JsonFormat.Printer jsonPrinter;
  private final JsonFormat.Parser jsonParser;

  public JsonMessageConverter() {
    jsonPrinter = JsonFormat.printer();
    jsonParser = JsonFormat.parser().ignoringUnknownFields();
  }

  public String toJson(DynamicMessage message) throws InvalidProtocolBufferException {
    return jsonPrinter.print(message);
  }

  public DynamicMessage toMessage(String json, Descriptors.Descriptor descriptor)
      throws InvalidProtocolBufferException {
    DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
    jsonParser.merge(json, builder);
    return builder.build();
  }
}
