package dev.horizon.spectrum.conformance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.util.function.Supplier;

final class ProtoDeserializer<M extends Message> extends JsonDeserializer<M> {

  private final Supplier<Message.Builder> builder;

  ProtoDeserializer(Supplier<Message.Builder> builder) {
    this.builder = builder;
  }

  @Override
  @SuppressWarnings("unchecked")
  public M deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String json = parser.readValueAsTree().toString();
    Message.Builder message = builder.get();
    try {
      JsonFormat.parser().merge(json, message);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IOException(json, invalid);
    }
    return (M) message.build();
  }
}
