package dev.horizon.spectrum;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;
import static dev.horizon.basepoint.Filter.gt;
import static dev.horizon.basepoint.Filter.lt;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import dev.horizon.basepoint.Direction;
import dev.horizon.basepoint.Query;
import dev.horizon.spectrum.v1.Assertion;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.ModelAssertions;
import dev.horizon.spectrum.v1.Store;
import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * The stores of a database, as rows of the store table, and their models, as rows of the model table.
 */
final class StoresImpl implements Stores {

  private static final String STORES = "spectrum_store";
  private static final String MODELS = "spectrum_store_model";
  private static final String ASSERTIONS = "spectrum_model_assertions";

  private final Database database;
  private final UlidGenerator ids;

  StoresImpl(Database database, UlidGenerator ids) {
    this.database = database;
    this.ids = ids;
  }

  @Override
  public Stores on(SqlClient client) {
    return new StoresImpl(database.on(client), ids);
  }

  @Override
  public Future<Store> create(String name) {
    if (name.isEmpty() || name.length() > 255) {
      throw new IllegalArgumentException("the name of a store has 1 to 255 characters, not " + name.length());
    }
    String id = ids.next();
    return database.count(database.table(STORES).insert(Map.of("id", id, "name", name))).compose(added -> get(id));
  }

  @Override
  public Future<Store> get(String id) {
    return database.rows(database.table(STORES).query().where(eq("id", Ref.store(id)))).map(rows -> {
      if (rows.size() == 0) {
        throw new NoSuchElementException("the store '" + id + "' does not exist");
      }
      return store(rows.iterator().next());
    });
  }

  @Override
  public Future<Page<Store>> list(String name, int size, String token) {
    Query query = database.table(STORES).query();
    if (!name.isEmpty()) {
      query = query.where(eq("name", name));
    }
    if (!token.isEmpty()) {
      query = query.where(gt("id", Cursor.of(token, 1).keys().getFirst()));
    }
    return page(query.orderBy("id"), size, this::store, Store::getId);
  }

  @Override
  public Future<Boolean> delete(String id) {
    Ref.store(id);
    return database.inTransaction(tx -> tx.count(tx.table(RelationshipTable.NAME).delete().where(eq("store_id", id)))
        .compose(relationships -> tx.count(tx.table(ChangeLog.NAME).delete().where(eq("store_id", id))))
        .compose(changes -> tx.count(tx.table(ASSERTIONS).delete().where(eq("store_id", id))))
        .compose(assertions -> tx.count(tx.table(MODELS).delete().where(eq("store_id", id))))
        .compose(models -> tx.count(tx.table(STORES).delete().where(eq("id", id))))
        .map(deleted -> deleted > 0));
  }

  @Override
  public Future<Model> writeModel(String store, Model model) {
    new Definitions(model);
    String id = ids.next();
    String definition;
    try {
      definition = JsonFormat.printer().omittingInsignificantWhitespace().print(model.toBuilder().clearId());
    } catch (InvalidProtocolBufferException impossible) {
      throw new IllegalStateException(impossible);
    }
    return get(store).compose(found -> database.count(database.table(MODELS).insert(Map.of("id", id, "store_id", store,
        "definition", definition)))).map(added -> model.toBuilder().setId(id).build());
  }

  @Override
  public Future<Model> readModel(String store, String id) {
    return get(store).compose(found -> database.rows(database.table(MODELS).query().where(and(eq("store_id", store),
        eq("id", id))))).map(rows -> {
          if (rows.size() == 0) {
            throw new NoSuchElementException("the store '" + store + "' has no model '" + id + "'");
          }
          return model(rows.iterator().next());
        });
  }

  @Override
  public Future<Model> latestModel(String store) {
    return get(store).compose(found -> database.rows(database.table(MODELS).query().where(eq("store_id", store))
        .orderBy("id", Direction.DESC).limit(1))).map(rows -> {
          if (rows.size() == 0) {
            throw new NoSuchElementException("the store '" + store + "' has no model");
          }
          return model(rows.iterator().next());
        });
  }

  @Override
  public Future<Page<Model>> listModels(String store, int size, String token) {
    if (size < 1) {
      throw new IllegalArgumentException("the size of a page is " + size + ", not at least 1");
    }
    Query query = database.table(MODELS).query().where(eq("store_id", Ref.store(store)));
    if (!token.isEmpty()) {
      query = query.where(lt("id", Cursor.of(token, 1).keys().getFirst()));
    }
    Query models = query.orderBy("id", Direction.DESC);
    return get(store).compose(found -> page(models, size, this::model, Model::getId));
  }

  @Override
  public Future<Void> writeAssertions(String store, String model, List<Assertion> assertions) {
    return readModel(store, model).compose(found -> {
      RelationsImpl relations = new RelationsImpl(new RelationshipTable(database, store, ids), new Definitions(found),
          List.of());
      assertions.forEach(relations::validate);
      String definition;
      try {
        definition = JsonFormat.printer().omittingInsignificantWhitespace().print(ModelAssertions.newBuilder()
            .addAllAssertions(assertions));
      } catch (InvalidProtocolBufferException impossible) {
        throw new IllegalStateException(impossible);
      }
      return database.inTransaction(tx -> tx.count(tx.table(ASSERTIONS).delete().where(eq("model_id", model)))
          .compose(deleted -> tx.count(tx.table(ASSERTIONS).insert(Map.of("model_id", model, "store_id", store,
              "definition", definition))))
          .mapEmpty());
    });
  }

  @Override
  public Future<List<Assertion>> readAssertions(String store, String model) {
    return readModel(store, model).compose(found -> database.rows(database.table(ASSERTIONS).query()
        .where(and(eq("store_id", store), eq("model_id", model))))).map(rows -> {
          if (rows.size() == 0) {
            return List.of();
          }
          ModelAssertions.Builder kept = ModelAssertions.newBuilder();
          try {
            JsonFormat.parser().merge(database.text(rows.iterator().next(), "definition"), kept);
          } catch (InvalidProtocolBufferException invalid) {
            throw new IllegalStateException("the assertions of the model " + model + " are not assertions", invalid);
          }
          return kept.getAssertionsList();
        });
  }

  private <T> Future<Page<T>> page(Query query, int size, Function<Row, T> item, Function<T, String> key) {
    if (size < 1) {
      throw new IllegalArgumentException("the size of a page is " + size + ", not at least 1");
    }
    return database.rows(query.limit(size + 1L)).map(rows -> {
      List<T> items = new ArrayList<>();
      for (Row row : rows) {
        items.add(item.apply(row));
      }
      if (items.size() <= size) {
        return new Page<>(items, "");
      }
      List<T> page = items.subList(0, size);
      return new Page<>(page, new Cursor(List.of(key.apply(page.getLast()))).token());
    });
  }

  private Store store(Row row) {
    Store.Builder store = Store.newBuilder().setId(database.text(row, "id")).setName(database.text(row, "name"));
    var created = database.timestamp(row, "create_time");
    if (created != null) {
      store.setCreateTime(created);
    }
    return store.build();
  }

  private Model model(Row row) {
    Model.Builder model = Model.newBuilder();
    try {
      JsonFormat.parser().merge(database.text(row, "definition"), model);
    } catch (InvalidProtocolBufferException invalid) {
      throw new IllegalStateException("the model " + database.text(row, "id") + " is not a model", invalid);
    }
    return model.setId(database.text(row, "id")).build();
  }
}
