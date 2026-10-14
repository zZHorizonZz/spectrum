package dev.horizon.spectrum;

import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.v1.Assertion;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Store;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The stores of a database and the models that each keeps, as OpenFGA has them.
 *
 * <p>
 * A store holds relationships apart from those of every other store, and keeps each model that it is given. The model
 * with the greatest id is the latest. Spectrum gives a store and a model a ULID as its id, so that ids grow in the order
 * that they are made.
 *
 * <p>
 * A method fails with a {@link NoSuchElementException} through its future when the store or the model does not exist.
 */
public interface Stores {

  /**
   * Opens the stores of a database.
   *
   * @param client the client that runs the statements: a {@link Pool}, or a {@link SqlConnection}
   * @param dialect the SQL of the database, with the placeholders that the client takes
   * @return the stores
   */
  static Stores open(SqlClient client, Dialect dialect) {
    return new StoresImpl(new Database(client, dialect), new UlidGenerator(Clock.systemUTC()));
  }

  /**
   * Gives the same stores through another client, for example a connection in a transaction.
   *
   * @param client the client
   * @return the stores through the client
   */
  Stores on(SqlClient client);

  /**
   * Creates a store.
   *
   * @param name the name, 1 to 255 characters
   * @return the store
   * @throws IllegalArgumentException if the name is empty or too long
   */
  Future<Store> create(String name);

  /**
   * Gets a store.
   *
   * @param id the id of the store
   * @return the store
   */
  Future<Store> get(String id);

  /**
   * Lists the stores, in order of id.
   *
   * @param name the name of the stores to list, or empty for every store
   * @param size the most stores the page has, at least 1
   * @param token the token of the page, from {@link Page#next()}, or empty for the first page
   * @return the page
   * @throws IllegalArgumentException if the size or the token is not valid
   */
  Future<Page<Store>> list(String name, int size, String token);

  /**
   * Deletes a store, its models, assertions, relationships and changes, in one transaction.
   *
   * @param id the id of the store
   * @return true if the store was deleted, false if it did not exist
   */
  Future<Boolean> delete(String id);

  /**
   * Keeps a model in a store, as the latest.
   *
   * @param store the id of the store
   * @param model the model
   * @return the model with the id that the store gave it
   * @throws IllegalArgumentException if the model is not valid
   */
  Future<Model> writeModel(String store, Model model);

  /**
   * Reads a model of a store.
   *
   * @param store the id of the store
   * @param id the id of the model
   * @return the model, with its id
   */
  Future<Model> readModel(String store, String id);

  /**
   * Reads the latest model of a store.
   *
   * @param store the id of the store
   * @return the model with the greatest id, with its id
   */
  Future<Model> latestModel(String store);

  /**
   * Lists the models of a store, the latest first.
   *
   * @param store the id of the store
   * @param size the most models the page has, at least 1
   * @param token the token of the page, from {@link Page#next()}, or empty for the first page
   * @return the page
   * @throws IllegalArgumentException if the size or the token is not valid
   */
  Future<Page<Model>> listModels(String store, int size, String token);

  /**
   * Keeps the assertions of a model, in place of those it had.
   *
   * @param store the id of the store
   * @param model the id of the model
   * @param assertions the assertions, each a check that the model can run
   * @return a future that completes when the store keeps them
   */
  Future<Void> writeAssertions(String store, String model, List<Assertion> assertions);

  /**
   * Reads the assertions of a model.
   *
   * @param store the id of the store
   * @param model the id of the model
   * @return the assertions, in the order that they were written; none if the model has none
   */
  Future<List<Assertion>> readAssertions(String store, String model);
}
