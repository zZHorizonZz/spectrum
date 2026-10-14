package dev.horizon.spectrum.it;

import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;

final class ProtoText implements ArgumentConverter {

  @Override
  public Object convert(Object source, ParameterContext context) {
    return parse(context.getParameter().getType().asSubclass(Message.class), source == null ? "" : source.toString());
  }

  <T extends Message> T parse(Class<T> type, String text) {
    try {
      Message.Builder builder = (Message.Builder) type.getMethod("newBuilder").invoke(null);
      TextFormat.merge(text, builder);
      return type.cast(builder.build());
    } catch (ReflectiveOperationException | TextFormat.ParseException failed) {
      throw new ArgumentConversionException("'" + text + "' is not a " + type.getSimpleName(), failed);
    }
  }
}
