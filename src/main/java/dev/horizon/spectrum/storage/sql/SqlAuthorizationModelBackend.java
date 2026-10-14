package dev.horizon.spectrum.storage.sql;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.lte;

import dev.horizon.aquery.aip158.Page;
import dev.horizon.basepoint.Direction;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.storage.AuthorizationModelBackend;
import dev.horizon.spectrum.storage.PageQuery;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The models of the {@code authorization_model} table, each kept whole as a serialized message. Model IDs are ULIDs, so
 * the latest model has the greatest ID.
 */
public final class SqlAuthorizationModelBackend implements AuthorizationModelBackend {

  private static final String AUTHORIZATION_MODEL = "authorization_model";

  private final SqlDatabase database;

  public SqlAuthorizationModelBackend(SqlDatabase database) {
    this.database = database;
  }

  @Override
  public Future<Void> write(String store, AuthorizationModel model) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("store", store);
    row.put("authorization_model_id", model.getId());
    row.put("schema_version", model.getSchemaVersion());
    row.put("serialized_protobuf", database.types().binary(model.toByteArray()));
    return database.run(database.table(AUTHORIZATION_MODEL).insert(row)).mapEmpty();
  }

  @Override
  public Future<Optional<AuthorizationModel>> read(String store, String id) {
    return models(database.table(AUTHORIZATION_MODEL).query().where(and(eq("store", store),
        eq("authorization_model_id", id)))).map(models -> models.stream().findFirst());
  }

  @Override
  public Future<Page<AuthorizationModel>> readPage(String store, PageQuery page) {
    Query query = latestFirst(store).limit(page.size() + 1L);
    if (!page.from().isEmpty()) {
      query = query.where(lte("authorization_model_id", page.from()));
    }
    return models(query).map(models -> models.size() > page.size() ? page.page(models.subList(0, page.size()),
        List.of(models.get(page.size()).getId())) : page.page(models, List.of()));
  }

  @Override
  public Future<Optional<AuthorizationModel>> findLatest(String store) {
    return models(latestFirst(store).limit(1)).map(models -> models.stream().findFirst());
  }

  private Query latestFirst(String store) {
    return database.table(AUTHORIZATION_MODEL).query().where(eq("store", store)).orderBy("authorization_model_id",
        Direction.DESC);
  }

  private Future<List<AuthorizationModel>> models(Query query) {
    return database.run(query).map(rows -> {
      List<AuthorizationModel> models = new ArrayList<>(rows.size());
      rows.forEach(row -> models.add(database.types().message(row, "serialized_protobuf",
          AuthorizationModel.parser())));
      return models;
    });
  }
}
