package dev.horizon.spectrum.common;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.util.Map;

/**
 * Checks that the keys and texts of the context of a condition have no control characters, in nested lists and structs
 * too.
 */
public final class ContextValidator {

  public void validate(Struct context) {
    for (Map.Entry<String, Value> field : context.getFieldsMap().entrySet()) {
      if (forbidden(field.getKey())) {
        throw new IllegalArgumentException("context key \"" + field.getKey() + "\" contains forbidden characters");
      }
      validate(field.getValue());
    }
  }

  private void validate(Value value) {
    switch (value.getKindCase()) {
      case STRING_VALUE -> {
        if (forbidden(value.getStringValue())) {
          throw new IllegalArgumentException("context value \"" + value.getStringValue()
              + "\" contains forbidden characters");
        }
      }
      case LIST_VALUE -> value.getListValue().getValuesList().forEach(this::validate);
      case STRUCT_VALUE -> validate(value.getStructValue());
      default -> {
      }
    }
  }

  private boolean forbidden(String text) {
    return text.chars().anyMatch(Character::isISOControl);
  }
}
