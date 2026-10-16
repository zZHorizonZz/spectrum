package dev.horizon.spectrum.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.schema.v1.Tables;
import dev.horizon.spectrum.tuple.TupleId;
import dev.horizon.spectrum.tuple.TupleObject;
import dev.horizon.spectrum.tuple.TupleUser;
import java.util.Optional;

/**
 * Turns tuples and changes into the rows of the {@code tuple} and {@code changelog} tables, and back: an object splits
 * into its {@code object_type} and {@code object_id} columns, and a condition into its {@code condition_name} and
 * {@code condition_context} columns.
 */
public final class TupleRows {

  /**
   * Gives the row of a tuple.
   *
   * @param store the ID of the store
   * @param key the tuple, with its condition if it has one
   * @param ulid the ULID of the tuple
   * @return the row, without the time of the write, which the database sets
   */
  public Tables.Tuple tuple(String store, TupleKey key, String ulid) {
    TupleObject object = TupleObject.parse(key.getObject());
    Tables.Tuple.Builder row = Tables.Tuple.newBuilder()
        .setStore(store).setObjectType(object.type()).setObjectId(object.id()).setRelation(key.getRelation())
        .setUser(key.getUser()).setUserType(TupleUser.parse(key.getUser()).userType()).setUlid(ulid);
    if (key.hasCondition()) {
      row.setConditionName(key.getCondition().getName());
      context(key.getCondition()).ifPresent(row::setConditionContext);
    }
    return row.build();
  }

  /**
   * Gives the row of a change.
   *
   * @param store the ID of the store
   * @param key the tuple that changed
   * @param condition the condition of the tuple, or empty
   * @param operation whether the tuple was written or deleted
   * @param ulid the ULID of the change
   * @return the row, without the time of the change, which the database sets
   */
  public Tables.Changelog change(String store, TupleId key, Optional<RelationshipCondition> condition,
      TupleOperation operation, String ulid) {
    TupleObject object = TupleObject.parse(key.object());
    Tables.Changelog.Builder row = Tables.Changelog.newBuilder().setStore(store).setUlid(ulid).setObjectType(object.type())
        .setObjectId(object.id()).setRelation(key.relation()).setUser(key.user())
        .setOperation(operation.getNumber());
    condition.ifPresent(written -> {
      row.setConditionName(written.getName());
      context(written).ifPresent(row::setConditionContext);
    });
    return row.build();
  }

  /**
   * Reads a row of the {@code tuple} table.
   *
   * @param row the row
   * @return the tuple, with its condition and the time it was written
   */
  public Tuple tuple(Tables.Tuple row) {
    return Tuple.newBuilder().setKey(key(row.getObjectType(), row.getObjectId(), row.getRelation(), row.getUser(),
        row.getConditionName(), row.getConditionContext())).setTimestamp(row.getInsertedAt()).build();
  }

  /**
   * Reads a row of the {@code changelog} table.
   *
   * @param row the row
   * @return the change, with its operation and the time it was made
   */
  public TupleChange change(Tables.Changelog row) {
    return TupleChange.newBuilder().setTupleKey(key(row.getObjectType(), row.getObjectId(), row.getRelation(),
        row.getUser(), row.getConditionName(), row.getConditionContext()))
        .setOperation(TupleOperation.forNumber(row.getOperation())).setTimestamp(row.getInsertedAt()).build();
  }

  /**
   * Gives the context of a condition, or empty for none. An empty context stays NULL, because the SQL Server client
   * fails on an empty binary.
   */
  private static Optional<ByteString> context(RelationshipCondition condition) {
    return condition.getContext().getFieldsCount() > 0 ? Optional.of(condition.getContext().toByteString())
        : Optional.empty();
  }

  private static TupleKey key(String objectType, String objectId, String relation, String user, String condition,
      ByteString context) {
    TupleKey.Builder key = TupleKey.newBuilder().setObject(objectType + ":" + objectId).setRelation(relation)
        .setUser(user);
    if (!condition.isEmpty()) {
      RelationshipCondition.Builder written = key.getConditionBuilder().setName(condition);
      if (!context.isEmpty()) {
        try {
          written.setContext(Struct.parseFrom(context));
        } catch (InvalidProtocolBufferException corrupt) {
          throw new OpenFgaException(InternalErrorCode.internal_error, "a stored condition context is corrupt: "
              + corrupt.getMessage());
        }
      }
    }
    return key.build();
  }
}
