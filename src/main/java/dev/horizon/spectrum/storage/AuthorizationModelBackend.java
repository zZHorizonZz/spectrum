package dev.horizon.spectrum.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.lte;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Query;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import dev.horizon.spectrum.schema.v1.Tables;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the authorization models of a store. A model never changes once written, and is kept whole as a
 * serialized message. Model IDs are ULIDs, so the latest model has the greatest ID.
 *
 * @param database the database of the {@code authorization_model} table
 */
public record AuthorizationModelBackend(Database database)
    implements Backend<Tables.AuthorizationModel, Void> {

  @Override
  public Tables.AuthorizationModel prototype() {
    return Tables.AuthorizationModel.getDefaultInstance();
  }

  /**
   * Writes a new model.
   *
   * @param store the ID of the store
   * @param model the model, with its ID
   * @return a future that completes when the model is stored, or fails if the store has a model with the same ID
   */
  public Future<Void> write(String store, AuthorizationModel model) {
    return insert().values(Tables.AuthorizationModel.newBuilder().setStore(store)
        .setAuthorizationModelId(model.getId()).setSchemaVersion(model.getSchemaVersion())
        .setSerializedProtobuf(model.toByteString()).build()).execute().mapEmpty();
  }

  /**
   * Reads a model.
   *
   * @param store the ID of the store
   * @param id the ID of the model
   * @return the model, or empty if the store has no model with the ID
   */
  public Future<Optional<AuthorizationModel>> read(String store, String id) {
    return find(and(eq("store", store), eq("authorization_model_id", id))).first().map(found -> found.map(this::model));
  }

  /**
   * Reads a page of the models of a store, the latest first.
   *
   * @param store the ID of the store
   * @param page the page, whose cursor is the ID of its first model
   * @return the models of the page, with the token of the next page
   */
  public Future<Page<AuthorizationModel>> readPage(String store, PageQuery page) {
    Query<Tables.AuthorizationModel> query = latestFirst(store).limit(page.size() + 1L);
    if (!page.from().isEmpty()) {
      query = query.where(lte("authorization_model_id", page.from()));
    }
    return query.list().map(rows -> {
      List<AuthorizationModel> models = rows.stream().map(this::model).toList();
      return models.size() > page.size() ? page.page(models.subList(0, page.size()), List.of(models.get(page
          .size()).getId())) : page.page(models, List.of());
    });
  }

  /**
   * Reads the latest model of a store, the one with the greatest ID.
   *
   * @param store the ID of the store
   * @return the model, or empty if the store has none
   */
  public Future<Optional<AuthorizationModel>> findLatest(String store) {
    return latestFirst(store).first().map(found -> found.map(this::model));
  }

  private Query<Tables.AuthorizationModel> latestFirst(String store) {
    return find(eq("store", store)).orderBy("authorization_model_id desc");
  }

  private AuthorizationModel model(Tables.AuthorizationModel row) {
    try {
      return AuthorizationModel.parseFrom(row.getSerializedProtobuf());
    } catch (InvalidProtocolBufferException corrupt) {
      throw new OpenFgaException(InternalErrorCode.internal_error, "a stored model is corrupt: "
          + corrupt.getMessage());
    }
  }
}
