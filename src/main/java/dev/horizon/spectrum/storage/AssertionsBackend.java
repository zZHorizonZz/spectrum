package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Conflict;
import dev.horizon.cinder.Database;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.Assertion;
import dev.horizon.spectrum.openfga.v1.Assertions;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import dev.horizon.spectrum.schema.v1.Tables;
import io.vertx.core.Future;
import java.util.List;

/**
 * Reads and writes the assertions of the authorization models of a store: the checks that a model is expected to
 * answer. A model has one row, which holds all its assertions as one serialized message.
 *
 * @param database the database of the {@code assertion} table
 */
public record AssertionsBackend(Database database)
    implements Backend<Tables.Assertion, Void> {

  @Override
  public Tables.Assertion prototype() {
    return Tables.Assertion.getDefaultInstance();
  }

  /**
   * Writes the assertions of a model, in place of those it had.
   *
   * @param store the ID of the store
   * @param modelId the ID of the model
   * @param assertions the assertions, or none to clear them
   * @return a future that completes when the assertions are stored
   */
  public Future<Void> write(String store, String modelId, List<Assertion> assertions) {
    return insert().values(Tables.Assertion.newBuilder().setStore(store)
        .setAuthorizationModelId(modelId).setAssertions(Assertions.newBuilder().addAllAssertions(assertions).build()
            .toByteString())
        .build()).onConflict(Conflict.merge()).execute().mapEmpty();
  }

  /**
   * Reads the assertions of a model.
   *
   * @param store the ID of the store
   * @param modelId the ID of the model
   * @return the assertions, in the order they were written, or none if the model has none
   */
  public Future<List<Assertion>> read(String store, String modelId) {
    return find(and(eq("store", store), eq("authorization_model_id", modelId))).first().map(found -> found.map(row -> {
      try {
        return Assertions.parseFrom(row.getAssertions()).getAssertionsList();
      } catch (InvalidProtocolBufferException corrupt) {
        throw new OpenFgaException(InternalErrorCode.internal_error, "stored assertions are corrupt: "
            + corrupt.getMessage());
      }
    }).orElse(List.of()));
  }
}
