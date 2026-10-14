package dev.horizon.spectrum.storage.sql;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import io.vertx.sqlclient.Row;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The rows of the {@code tuple} and {@code changelog} tables: an object splits into its {@code object_type} and {@code
 * object_id} columns, and a condition into its {@code condition_name} and {@code condition_context} columns.
 */
public final class TupleRows {

  private final SqlTypes types;

  public TupleRows(SqlTypes types) {
    this.types = types;
  }

  public Map<String, Object> tuple(String store, TupleKey key, String ulid) {
    Map<String, Object> row = key(store, new TupleId(key));
    row.put("user_type", TupleUser.parse(key.getUser()).userType());
    row.put("ulid", ulid);
    if (key.hasCondition()) {
      condition(row, key.getCondition());
    }
    return row;
  }

  public Map<String, Object> change(String store, TupleId key, Optional<RelationshipCondition> condition,
      TupleOperation operation, String ulid) {
    Map<String, Object> row = key(store, key);
    row.put("ulid", ulid);
    row.put("operation", operation.getNumber());
    condition.ifPresent(written -> condition(row, written));
    return row;
  }

  public Tuple tuple(Row row) {
    return Tuple.newBuilder().setKey(key(row)).setTimestamp(types.timestamp(row, "inserted_at")).build();
  }

  public TupleChange change(Row row) {
    return TupleChange.newBuilder().setTupleKey(key(row))
        .setOperation(TupleOperation.forNumber(row.getInteger("operation")))
        .setTimestamp(types.timestamp(row, "inserted_at")).build();
  }

  private Map<String, Object> key(String store, TupleId key) {
    TupleObject object = TupleObject.parse(key.object());
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("store", store);
    row.put("object_type", object.type());
    row.put("object_id", object.id());
    row.put("relation", key.relation());
    row.put("_user", key.user());
    return row;
  }

  /** Puts a condition in a row. An empty context stays NULL, because the SQL Server client fails on an empty binary. */
  private void condition(Map<String, Object> row, RelationshipCondition condition) {
    row.put("condition_name", condition.getName());
    if (condition.getContext().getFieldsCount() > 0) {
      row.put("condition_context", types.binary(condition.getContext().toByteArray()));
    }
  }

  private TupleKey key(Row row) {
    TupleKey.Builder key = TupleKey.newBuilder().setObject(types.text(row, "object_type") + ":"
        + types.text(row, "object_id")).setRelation(types.text(row, "relation")).setUser(types.text(row, "_user"));
    String name = types.text(row, "condition_name");
    if (!name.isEmpty()) {
      RelationshipCondition.Builder condition = key.getConditionBuilder().setName(name);
      byte[] context = types.bytes(row, "condition_context");
      if (context != null && context.length > 0) {
        condition.setContext(types.message(row, "condition_context", Struct.parser()));
      }
    }
    return key.build();
  }
}
