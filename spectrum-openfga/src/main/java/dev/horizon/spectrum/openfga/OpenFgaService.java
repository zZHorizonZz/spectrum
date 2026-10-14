package dev.horizon.spectrum.openfga;

import com.google.protobuf.Timestamp;
import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.Relations;
import dev.horizon.spectrum.Stores;
import dev.horizon.spectrum.openfga.v1.AssertionTupleKey;
import dev.horizon.spectrum.openfga.v1.BatchCheckItem;
import dev.horizon.spectrum.openfga.v1.BatchCheckRequest;
import dev.horizon.spectrum.openfga.v1.BatchCheckResponse;
import dev.horizon.spectrum.openfga.v1.BatchCheckSingleResult;
import dev.horizon.spectrum.openfga.v1.CheckError;
import dev.horizon.spectrum.openfga.v1.CheckRequest;
import dev.horizon.spectrum.openfga.v1.CheckResponse;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.openfga.v1.DeleteStoreRequest;
import dev.horizon.spectrum.openfga.v1.DeleteStoreResponse;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.ExpandRequest;
import dev.horizon.spectrum.openfga.v1.ExpandResponse;
import dev.horizon.spectrum.openfga.v1.GetStoreRequest;
import dev.horizon.spectrum.openfga.v1.GetStoreResponse;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import dev.horizon.spectrum.openfga.v1.ListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.ListObjectsResponse;
import dev.horizon.spectrum.openfga.v1.ListStoresRequest;
import dev.horizon.spectrum.openfga.v1.ListStoresResponse;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.ListUsersResponse;
import dev.horizon.spectrum.openfga.v1.Object;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceService;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.ReadAssertionsResponse;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelResponse;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsRequest;
import dev.horizon.spectrum.openfga.v1.ReadAuthorizationModelsResponse;
import dev.horizon.spectrum.openfga.v1.ReadChangesRequest;
import dev.horizon.spectrum.openfga.v1.ReadChangesResponse;
import dev.horizon.spectrum.openfga.v1.ReadRequest;
import dev.horizon.spectrum.openfga.v1.ReadResponse;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.StreamedListObjectsResponse;
import dev.horizon.spectrum.openfga.v1.Tuple;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import dev.horizon.spectrum.openfga.v1.TypedWildcard;
import dev.horizon.spectrum.openfga.v1.UpdateStoreRequest;
import dev.horizon.spectrum.openfga.v1.UpdateStoreResponse;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import dev.horizon.spectrum.openfga.v1.WriteAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.WriteAssertionsResponse;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelResponse;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteResponse;
import dev.horizon.spectrum.v1.Assertion;
import dev.horizon.spectrum.v1.Change;
import dev.horizon.spectrum.v1.Model;
import dev.horizon.spectrum.v1.Operation;
import dev.horizon.spectrum.v1.Relationship;
import dev.horizon.spectrum.v1.Store;
import dev.horizon.spectrum.v1.SubjectSet;
import io.vertx.core.Future;
import io.vertx.core.streams.WriteStream;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.GrpcServerResponse;
import io.vertx.grpc.server.StatusException;
import io.vertx.sqlclient.Pool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The OpenFGA service on Spectrum: the stores, models and relationships of a database, as OpenFGA's API has them.
 *
 * <p>
 * Turn it into a gRPC service with {@code OpenFGAServiceGrpcService.of(service)}, and add that to an
 * {@code EventBusGrpcServer} for clients in the same Vert.x instance or cluster, or to a {@code GrpcServer} for clients
 * over HTTP. Both serve the same service.
 *
 * <p>
 * Spectrum keeps every write in the database at once, so a consistency preference changes nothing. Conditions and
 * Expand fail with {@code UNIMPLEMENTED}.
 */
public class OpenFgaService extends OpenFGAServiceService {

  /** The most tuples that one write takes, as OpenFGA's default. */
  static final int MAX_TUPLES_PER_WRITE = 100;

  /** The most assertions that a model takes, as OpenFGA's limit. */
  static final int MAX_ASSERTIONS = 100;

  private static final System.Logger LOGGER = System.getLogger(OpenFgaService.class.getName());

  /** The message of a failure that Spectrum did not raise on purpose; the log holds its details. */
  private static final String INTERNAL_ERROR = "an internal error occurred";

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 100;

  private final Pool pool;
  private final Dialect dialect;
  private final Stores stores;
  private final ModelTranslation translation = new ModelTranslation();
  private final Map<String, Relations> opened = new ConcurrentHashMap<>();

  /**
   * Creates the service.
   *
   * @param pool the pool that runs the statements
   * @param dialect the SQL of the database, with the placeholders that the pool takes
   */
  public OpenFgaService(Pool pool, Dialect dialect) {
    this.pool = pool;
    this.dialect = dialect;
    this.stores = Stores.open(pool, dialect);
  }

  @Override
  public Future<CreateStoreResponse> createStore(CreateStoreRequest request) {
    return call(() -> stores.create(request.getName()).map(store -> CreateStoreResponse.newBuilder()
        .setId(store.getId()).setName(store.getName()).setCreatedAt(store.getCreateTime())
        .setUpdatedAt(store.getCreateTime()).build()));
  }

  @Override
  public Future<GetStoreResponse> getStore(GetStoreRequest request) {
    return call(() -> stores.get(request.getStoreId()).map(store -> GetStoreResponse.newBuilder()
        .setId(store.getId()).setName(store.getName()).setCreatedAt(store.getCreateTime())
        .setUpdatedAt(store.getCreateTime()).build()));
  }

  @Override
  public Future<ListStoresResponse> listStores(ListStoresRequest request) {
    return call(() -> stores.list(request.getName(), size(request.hasPageSize(), request.getPageSize().getValue()),
        request.getContinuationToken()).map(
            page -> ListStoresResponse.newBuilder()
                .addAllStores(page.items().stream().map(this::store).toList()).setContinuationToken(page.next())
                .build()));
  }

  @Override
  public Future<DeleteStoreResponse> deleteStore(DeleteStoreRequest request) {
    return call(() -> stores.delete(request.getStoreId()).map(deleted -> {
      if (!deleted) {
        throw new NoSuchElementException("the store '" + request.getStoreId() + "' does not exist");
      }
      opened.keySet().removeIf(key -> key.startsWith(request.getStoreId() + "/"));
      return DeleteStoreResponse.getDefaultInstance();
    }));
  }

  @Override
  public Future<UpdateStoreResponse> updateStore(UpdateStoreRequest request) {
    return unimplemented("UpdateStore");
  }

  @Override
  public Future<WriteAuthorizationModelResponse> writeAuthorizationModel(WriteAuthorizationModelRequest request) {
    return call(() -> stores.writeModel(request.getStoreId(), translation.toSpectrum(request.getSchemaVersion(),
        request.getTypeDefinitionsList(), request.getConditionsMap())).map(
            model -> WriteAuthorizationModelResponse
                .newBuilder().setAuthorizationModelId(model.getId()).build()));
  }

  @Override
  public Future<ReadAuthorizationModelResponse> readAuthorizationModel(ReadAuthorizationModelRequest request) {
    return call(() -> stores.readModel(request.getStoreId(), request.getId())
        .map(model -> ReadAuthorizationModelResponse.newBuilder().setAuthorizationModel(translation.toOpenFga(model)).build()));
  }

  @Override
  public Future<ReadAuthorizationModelsResponse> readAuthorizationModels(ReadAuthorizationModelsRequest request) {
    return call(() -> stores.listModels(request.getStoreId(), size(request.hasPageSize(),
        request.getPageSize().getValue()), request.getContinuationToken()).map(
            page -> ReadAuthorizationModelsResponse
                .newBuilder().addAllAuthorizationModels(page.items().stream().map(translation::toOpenFga).toList())
                .setContinuationToken(page.next()).build()));
  }

  @Override
  public Future<WriteResponse> write(WriteRequest request) {
    return call(() -> {
      List<TupleKey> writes = request.getWrites().getTupleKeysList();
      List<TupleKeyWithoutCondition> deletes = request.getDeletes().getTupleKeysList();
      if (writes.isEmpty() && deletes.isEmpty()) {
        throw new IllegalArgumentException("a write needs at least one tuple to write or delete");
      }
      if (writes.size() + deletes.size() > MAX_TUPLES_PER_WRITE) {
        throw new IllegalArgumentException("a write takes at most " + MAX_TUPLES_PER_WRITE + " tuples, not "
            + (writes.size() + deletes.size()));
      }
      boolean ignoreDuplicates = policy("on_duplicate", request.getWrites().getOnDuplicate());
      boolean ignoreMissing = policy("on_missing", request.getDeletes().getOnMissing());
      List<Relationship> written = writes.stream().map(this::relationship).toList();
      List<Relationship> deleted = deletes.stream().map(key -> relationship(key.getUser(), key.getRelation(),
          key.getObject())).toList();
      Set<Relationship> seen = new HashSet<>();
      for (Relationship relationship : concat(written, deleted)) {
        if (!seen.add(relationship)) {
          throw new IllegalArgumentException("the tuple " + text(relationship) + " is in the write two times");
        }
      }
      return relations(request.getStoreId(), request.getAuthorizationModelId(), List.of())
          .compose(relations -> pool.withTransaction(connection -> {
            Relations in = relations.on(connection);
            Future<Void> done = Future.succeededFuture();
            for (Relationship relationship : deleted) {
              done = done.compose(ignored -> in.delete(relationship).map(found -> {
                if (!found && !ignoreMissing) {
                  throw new IllegalArgumentException("cannot delete a tuple which does not exist: "
                      + text(relationship));
                }
                return null;
              }));
            }
            for (Relationship relationship : written) {
              done = done.compose(ignored -> in.write(relationship).map(added -> {
                if (!added && !ignoreDuplicates) {
                  throw new IllegalArgumentException("cannot write a tuple which already exists: "
                      + text(relationship));
                }
                return null;
              }));
            }
            return done;
          })).map(done -> WriteResponse.getDefaultInstance());
    });
  }

  @Override
  public Future<ReadResponse> read(ReadRequest request) {
    return call(() -> {
      Relationship filter = Relationship.newBuilder().setObject(request.getTupleKey().getObject())
          .setRelation(request.getTupleKey().getRelation()).setSubject(request.getTupleKey().getUser()).build();
      Relations relations = Relations.open(pool, dialect, request.getStoreId(), Model.getDefaultInstance());
      int size = size(request.hasPageSize(), request.getPageSize().getValue());
      return stores.get(request.getStoreId())
          .compose(store -> relations.read(filter, size, request.getContinuationToken()))
          .map(page -> ReadResponse.newBuilder().addAllTuples(page.items().stream().map(this::tuple).toList())
              .setContinuationToken(page.next()).build());
    });
  }

  @Override
  public Future<CheckResponse> check(CheckRequest request) {
    return call(() -> relations(request.getStoreId(), request.getAuthorizationModelId(),
        request.getContextualTuples().getTupleKeysList())
        .compose(relations -> relations.check(request.getTupleKey().getUser(), request.getTupleKey().getRelation(),
            request.getTupleKey().getObject()))
        .map(allowed -> CheckResponse.newBuilder().setAllowed(allowed).build()));
  }

  @Override
  public Future<BatchCheckResponse> batchCheck(BatchCheckRequest request) {
    return call(() -> {
      if (request.getChecksCount() == 0) {
        throw new IllegalArgumentException("a batch check needs at least one check");
      }
      Set<String> ids = new HashSet<>();
      for (BatchCheckItem item : request.getChecksList()) {
        if (item.getCorrelationId().isEmpty() || !ids.add(item.getCorrelationId())) {
          throw new IllegalArgumentException("the correlation id '" + item.getCorrelationId() + "' is empty or in the"
              + " batch two times");
        }
      }
      return relations(request.getStoreId(), request.getAuthorizationModelId(), List.of()).compose(relations -> {
        Map<String, Future<BatchCheckSingleResult>> results = new LinkedHashMap<>();
        for (BatchCheckItem item : request.getChecksList()) {
          results.put(item.getCorrelationId(), Future.<Void> succeededFuture()
              .compose(ignored -> relations.with(item.getContextualTuples().getTupleKeysList().stream()
                  .map(this::relationship).toList()).check(item.getTupleKey().getUser(),
                      item.getTupleKey().getRelation(), item.getTupleKey().getObject()))
              .map(allowed -> BatchCheckSingleResult.newBuilder().setAllowed(allowed).build())
              .recover(failed -> Future.succeededFuture(BatchCheckSingleResult.newBuilder().setError(error(failed))
                  .build())));
        }
        return Future.all(new ArrayList<>(results.values())).map(all -> {
          BatchCheckResponse.Builder response = BatchCheckResponse.newBuilder();
          results.forEach((id, result) -> response.putResult(id, result.result()));
          return response.build();
        });
      });
    });
  }

  @Override
  public Future<ListObjectsResponse> listObjects(ListObjectsRequest request) {
    return call(() -> relations(request.getStoreId(), request.getAuthorizationModelId(),
        request.getContextualTuples().getTupleKeysList())
        .compose(relations -> relations.listObjects(request.getUser(), request.getRelation(), request.getType()))
        .map(objects -> ListObjectsResponse.newBuilder().addAllObjects(objects).build()));
  }

  @Override
  public Future<ListUsersResponse> listUsers(ListUsersRequest request) {
    return call(() -> {
      if (request.getUserFiltersCount() != 1) {
        throw new IllegalArgumentException("a list of users needs exactly one user filter, not "
            + request.getUserFiltersCount());
      }
      UserTypeFilter filter = request.getUserFilters(0);
      if (!filter.getRelation().isEmpty()) {
        throw new UnsupportedOperationException("user filters with a relation are not supported: " + filter.getType()
            + "#" + filter.getRelation());
      }
      Object object = request.getObject();
      return relations(request.getStoreId(), request.getAuthorizationModelId(), request.getContextualTuplesList())
          .compose(relations -> relations.listSubjects(object.getType() + ":" + object.getId(), request.getRelation(),
              filter.getType()))
          .map(subjects -> ListUsersResponse.newBuilder().addAllUsers(users(subjects, filter.getType())).build());
    });
  }

  @Override
  public Future<ExpandResponse> expand(ExpandRequest request) {
    return unimplemented("Expand");
  }

  @Override
  public Future<ReadChangesResponse> readChanges(ReadChangesRequest request) {
    return call(() -> {
      int size = size(request.hasPageSize(), request.getPageSize().getValue());
      Instant since = request.hasStartTime() ? Instant.ofEpochSecond(request.getStartTime().getSeconds(),
          request.getStartTime().getNanos()) : Instant.EPOCH;
      Relations relations = Relations.open(pool, dialect, request.getStoreId(), Model.getDefaultInstance());
      return stores.get(request.getStoreId())
          .compose(store -> relations.changes(request.getType(), since, size, request.getContinuationToken()))
          .map(page -> ReadChangesResponse.newBuilder().addAllChanges(page.items().stream().map(this::change).toList())
              .setContinuationToken(page.next()).build());
    });
  }

  @Override
  public Future<WriteAssertionsResponse> writeAssertions(WriteAssertionsRequest request) {
    return call(() -> {
      if (request.getAssertionsCount() > MAX_ASSERTIONS) {
        throw new IllegalArgumentException("a model takes at most " + MAX_ASSERTIONS + " assertions, not "
            + request.getAssertionsCount());
      }
      List<Assertion> assertions = request.getAssertionsList().stream().map(assertion -> Assertion.newBuilder()
          .setSubject(assertion.getTupleKey().getUser()).setRelation(assertion.getTupleKey().getRelation())
          .setObject(assertion.getTupleKey().getObject()).setExpectation(assertion.getExpectation())
          .addAllContextual(assertion.getContextualTuplesList().stream().map(this::relationship).toList()).build())
          .toList();
      return stores.writeAssertions(request.getStoreId(), request.getAuthorizationModelId(), assertions)
          .map(done -> WriteAssertionsResponse.getDefaultInstance());
    });
  }

  @Override
  public Future<ReadAssertionsResponse> readAssertions(ReadAssertionsRequest request) {
    return call(() -> stores.readAssertions(request.getStoreId(), request.getAuthorizationModelId())
        .map(assertions -> ReadAssertionsResponse.newBuilder().setAuthorizationModelId(request.getAuthorizationModelId())
            .addAllAssertions(assertions.stream().map(assertion -> dev.horizon.spectrum.openfga.v1.Assertion
                .newBuilder().setTupleKey(AssertionTupleKey.newBuilder().setUser(assertion.getSubject())
                    .setRelation(assertion.getRelation()).setObject(assertion.getObject()))
                .setExpectation(assertion.getExpectation())
                .addAllContextualTuples(assertion.getContextualList().stream().map(relationship -> TupleKey
                    .newBuilder().setUser(relationship.getSubject()).setRelation(relationship.getRelation())
                    .setObject(relationship.getObject()).build()).toList())
                .build()).toList())
            .build()));
  }

  @Override
  protected void streamedListObjects(StreamedListObjectsRequest request,
      WriteStream<StreamedListObjectsResponse> response) {
    call(() -> relations(request.getStoreId(), request.getAuthorizationModelId(),
        request.getContextualTuples().getTupleKeysList())
        .compose(relations -> relations.listObjects(request.getUser(), request.getRelation(), request.getType())))
        .onSuccess(objects -> {
          objects.forEach(object -> response.write(StreamedListObjectsResponse.newBuilder().setObject(object).build()));
          response.end();
        })
        .onFailure(failed -> {
          if (response instanceof GrpcServerResponse<?, ?> grpc) {
            grpc.fail(failed);
          } else {
            response.end();
          }
        });
  }

  /**
   * Opens the relationships of a store with a model, the latest if no id is given, and contextual tuples.
   */
  private Future<Relations> relations(String store, String model, List<TupleKey> contextual) {
    List<Relationship> relationships = contextual.stream().map(this::relationship).toList();
    Future<Relations> relations = model.isEmpty()
        ? stores.latestModel(store).map(latest -> opened(store, latest))
        : opened.containsKey(store + "/" + model) ? Future.succeededFuture(opened.get(store + "/" + model))
            : stores.readModel(store, model).map(found -> opened(store, found));
    return relationships.isEmpty() ? relations : relations.map(open -> open.with(relationships));
  }

  private Relations opened(String store, Model model) {
    return opened.computeIfAbsent(store + "/" + model.getId(), key -> Relations.open(pool, dialect, store, model));
  }

  private Relationship relationship(TupleKey key) {
    if (key.hasCondition()) {
      throw new UnsupportedOperationException("conditions are not supported: " + key.getCondition().getName());
    }
    return relationship(key.getUser(), key.getRelation(), key.getObject());
  }

  private static Relationship relationship(String user, String relation, String object) {
    return Relationship.newBuilder().setObject(object).setRelation(relation).setSubject(user).build();
  }

  private static String text(Relationship relationship) {
    return "user: '" + relationship.getSubject() + "', relation: '" + relationship.getRelation() + "', object: '"
        + relationship.getObject() + "'";
  }

  private static List<Relationship> concat(List<Relationship> first, List<Relationship> second) {
    List<Relationship> all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

  /** Reads an {@code on_duplicate} or {@code on_missing} policy: true to ignore, false to fail. */
  private static boolean policy(String field, String value) {
    return switch (value) {
      case "", "error" -> false;
      case "ignore" -> true;
      default -> throw new IllegalArgumentException("the " + field + " policy '" + value + "' is not error or ignore");
    };
  }

  private static int size(boolean given, int size) {
    if (!given) {
      return DEFAULT_PAGE_SIZE;
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("the page size is " + size + ", not 1 to " + MAX_PAGE_SIZE);
    }
    return size;
  }

  private dev.horizon.spectrum.openfga.v1.Store store(Store store) {
    return dev.horizon.spectrum.openfga.v1.Store.newBuilder().setId(store.getId()).setName(store.getName())
        .setCreatedAt(store.getCreateTime()).setUpdatedAt(store.getCreateTime()).build();
  }

  private TupleChange change(Change change) {
    return TupleChange.newBuilder().setTupleKey(TupleKey.newBuilder().setUser(change.getSubject())
        .setRelation(change.getRelation()).setObject(change.getObject()))
        .setOperation(change.getOperation() == Operation.OPERATION_DELETE ? TupleOperation.TUPLE_OPERATION_DELETE
            : TupleOperation.TUPLE_OPERATION_WRITE)
        .setTimestamp(change.getCreateTime()).build();
  }

  private Tuple tuple(Relationship relationship) {
    Timestamp time = relationship.getCreateTime();
    return Tuple.newBuilder().setKey(TupleKey.newBuilder().setUser(relationship.getSubject())
        .setRelation(relationship.getRelation()).setObject(relationship.getObject())).setTimestamp(time).build();
  }

  /**
   * Gives the users of a subject set. Every user but some is the typed wildcard, as OpenFGA gives it: a list of users
   * cannot name the users that are left out.
   */
  private static List<User> users(SubjectSet subjects, String type) {
    if (subjects.getAll()) {
      return List.of(User.newBuilder().setWildcard(TypedWildcard.newBuilder().setType(type)).build());
    }
    return subjects.getSubjectsList().stream().map(subject -> User.newBuilder().setObject(Object.newBuilder()
        .setType(type).setId(subject.substring(subject.indexOf(':') + 1))).build()).toList();
  }

  private static CheckError error(Throwable failed) {
    return failed instanceof IllegalArgumentException || failed instanceof UnsupportedOperationException
        ? CheckError.newBuilder().setInputError(ErrorCode.validation_error).setMessage(failed.getMessage()).build()
        : internal(failed);
  }

  private static CheckError internal(Throwable failed) {
    LOGGER.log(System.Logger.Level.ERROR, "a check of a batch failed", failed);
    return CheckError.newBuilder().setInternalError(InternalErrorCode.internal_error).setMessage(INTERNAL_ERROR).build();
  }

  private static <T> Future<T> unimplemented(String method) {
    return Future.failedFuture(new StatusException(GrpcStatus.UNIMPLEMENTED, method + " is not implemented by"
        + " Spectrum"));
  }

  /** Runs a call, and gives each failure, also one that the call throws, the gRPC status that OpenFGA gives it. */
  private static <T> Future<T> call(Supplier<Future<T>> call) {
    Future<T> result;
    try {
      result = call.get();
    } catch (RuntimeException failed) {
      result = Future.failedFuture(failed);
    }
    return result.recover(failed -> Future.failedFuture(switch (failed) {
      case StatusException status -> status;
      case IllegalArgumentException invalid -> new StatusException(GrpcStatus.INVALID_ARGUMENT, invalid.getMessage());
      case NoSuchElementException missing -> new StatusException(GrpcStatus.NOT_FOUND, missing.getMessage());
      case UnsupportedOperationException unsupported -> new StatusException(GrpcStatus.UNIMPLEMENTED,
          unsupported.getMessage());
      case IllegalStateException tooDeep -> new StatusException(GrpcStatus.INVALID_ARGUMENT, tooDeep.getMessage());
      default -> {
        LOGGER.log(System.Logger.Level.ERROR, "an OpenFGA call failed", failed);
        yield new StatusException(GrpcStatus.INTERNAL, INTERNAL_ERROR);
      }
    }));
  }
}
