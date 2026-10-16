package dev.horizon.spectrum;

import com.google.protobuf.Descriptors;
import dev.horizon.aquery.aip158.PageTokenCodec;
import dev.horizon.basepoint.Dialect;
import dev.horizon.spectrum.handler.BatchCheckHandler;
import dev.horizon.spectrum.handler.CheckHandler;
import dev.horizon.spectrum.handler.CreateStoreHandler;
import dev.horizon.spectrum.handler.DeleteStoreHandler;
import dev.horizon.spectrum.handler.ExpandHandler;
import dev.horizon.spectrum.handler.GetStoreHandler;
import dev.horizon.spectrum.handler.ListObjectsHandler;
import dev.horizon.spectrum.handler.ListStoresHandler;
import dev.horizon.spectrum.handler.ListUsersHandler;
import dev.horizon.spectrum.handler.OpenFgaHandler;
import dev.horizon.spectrum.handler.ReadAssertionsHandler;
import dev.horizon.spectrum.handler.ReadAuthorizationModelHandler;
import dev.horizon.spectrum.handler.ReadAuthorizationModelsHandler;
import dev.horizon.spectrum.handler.ReadChangesHandler;
import dev.horizon.spectrum.handler.ReadHandler;
import dev.horizon.spectrum.handler.StreamedListObjectsHandler;
import dev.horizon.spectrum.handler.UpdateStoreHandler;
import dev.horizon.spectrum.handler.WriteAssertionsHandler;
import dev.horizon.spectrum.handler.WriteAuthorizationModelHandler;
import dev.horizon.spectrum.handler.WriteHandler;
import dev.horizon.spectrum.listobjects.ListObjectsQuery;
import dev.horizon.spectrum.listusers.ListUsersQuery;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.OpenfgaServiceProto;
import dev.horizon.spectrum.storage.AssertionsBackend;
import dev.horizon.spectrum.storage.AuthorizationModelBackend;
import dev.horizon.spectrum.storage.StoresBackend;
import dev.horizon.spectrum.storage.TupleBackend;
import dev.horizon.spectrum.storage.sql.SqlAssertionsBackend;
import dev.horizon.spectrum.storage.sql.SqlAuthorizationModelBackend;
import dev.horizon.spectrum.storage.sql.SqlChangelogBackend;
import dev.horizon.spectrum.storage.sql.SqlDatabase;
import dev.horizon.spectrum.storage.sql.SqlStoresBackend;
import dev.horizon.spectrum.storage.sql.SqlTupleBackend;
import dev.horizon.spectrum.storage.sql.SqlTypes;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.sqlclient.Pool;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The service on the SQL backends of a database: one handler per method of {@code openfga.v1.OpenFGAService}. Create it
 * with {@link OpenFgaService#create}.
 */
public final class OpenFgaServiceImpl implements OpenFgaService {

  private static final Descriptors.ServiceDescriptor DESCRIPTOR = OpenfgaServiceProto.getDescriptor()
      .findServiceByName(OpenFgaHandler.SERVICE_NAME.name());

  private final List<ServiceMethod<?, ?>> methods = new ArrayList<>();
  private final Map<String, Handler<?>> handlers = new HashMap<>();

  public OpenFgaServiceImpl(Vertx vertx, Pool pool, Dialect dialect, OpenFgaServiceOptions options) {
    SqlDatabase database = new SqlDatabase(pool, dialect, SqlTypes.of(dialect), Clock.systemUTC());
    StoresBackend stores = new SqlStoresBackend(database);
    AuthorizationModelBackend models = new SqlAuthorizationModelBackend(database);
    TupleBackend tuples = new SqlTupleBackend(database);
    AssertionsBackend assertions = new SqlAssertionsBackend(database);
    PageTokenCodec tokens = new PageTokenCodec(options.pageTokenKey());
    TypeSystemResolver resolver = new TypeSystemResolver(models);
    CheckHandler check = new CheckHandler(resolver, tuples, options);
    ListObjectsQuery listObjects = new ListObjectsQuery(vertx, tuples, options);
    bind(CreateStoreHandler.SERVICE_METHOD, new CreateStoreHandler(stores));
    bind(GetStoreHandler.SERVICE_METHOD, new GetStoreHandler(stores));
    bind(ListStoresHandler.SERVICE_METHOD, new ListStoresHandler(stores, tokens));
    bind(UpdateStoreHandler.SERVICE_METHOD, new UpdateStoreHandler(stores));
    bind(DeleteStoreHandler.SERVICE_METHOD, new DeleteStoreHandler(stores));
    bind(WriteAuthorizationModelHandler.SERVICE_METHOD, new WriteAuthorizationModelHandler(models, options));
    bind(ReadAuthorizationModelHandler.SERVICE_METHOD, new ReadAuthorizationModelHandler(models));
    bind(ReadAuthorizationModelsHandler.SERVICE_METHOD, new ReadAuthorizationModelsHandler(models, tokens));
    bind(WriteHandler.SERVICE_METHOD, new WriteHandler(resolver, tuples, options));
    bind(ReadHandler.SERVICE_METHOD, new ReadHandler(tuples, tokens));
    bind(ReadChangesHandler.SERVICE_METHOD, new ReadChangesHandler(new SqlChangelogBackend(database), tokens));
    bind(WriteAssertionsHandler.SERVICE_METHOD, new WriteAssertionsHandler(resolver, assertions));
    bind(ReadAssertionsHandler.SERVICE_METHOD, new ReadAssertionsHandler(resolver, assertions));
    bind(CheckHandler.SERVICE_METHOD, check);
    bind(BatchCheckHandler.SERVICE_METHOD, new BatchCheckHandler(resolver, check, options));
    bind(ExpandHandler.SERVICE_METHOD, new ExpandHandler(resolver, tuples));
    bind(ListObjectsHandler.SERVICE_METHOD, new ListObjectsHandler(resolver, listObjects));
    bind(StreamedListObjectsHandler.SERVICE_METHOD, new StreamedListObjectsHandler(resolver, listObjects));
    bind(ListUsersHandler.SERVICE_METHOD, new ListUsersHandler(resolver, new ListUsersQuery(vertx, tuples, options)));
  }

  @Override
  public ServiceName name() {
    return OpenFgaHandler.SERVICE_NAME;
  }

  @Override
  public Descriptors.ServiceDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Override
  public List<ServiceMethod<?, ?>> methods() {
    return List.copyOf(methods);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Req, Resp> Handler<GrpcServerRequest<Req, Resp>> handler(ServiceMethod<Req, Resp> method) {
    Handler<?> handler = handlers.get(method.methodName());
    return handler == null ? OpenFgaService.super.handler(method) : (Handler<GrpcServerRequest<Req, Resp>>) handler;
  }

  private <Req, Resp> void bind(ServiceMethod<Req, Resp> method, Handler<GrpcServerRequest<Req, Resp>> handler) {
    methods.add(method);
    handlers.put(method.methodName(), handler);
  }
}
