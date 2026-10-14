package dev.horizon.spectrum.handler;

import com.google.protobuf.Struct;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.OpenFgaServiceOptions;
import dev.horizon.spectrum.check.CheckResult;
import dev.horizon.spectrum.check.LocalChecker;
import dev.horizon.spectrum.check.ResolveCheckRequest;
import dev.horizon.spectrum.common.ContextValidator;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.CheckRequest;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.CheckResponse;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.storage.CombinedTupleReader;
import dev.horizon.spectrum.storage.TupleReader;
import dev.horizon.spectrum.tuple.InvalidTupleException;
import dev.horizon.spectrum.tuple.KeyValidator;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.WriteValidator;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.List;

public final class CheckHandler extends UnaryHandler<CheckRequest, CheckResponse> {

  public static final ServiceMethod<CheckRequest, CheckResponse> SERVICE_METHOD = method("Check",
      CheckRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final TupleReader reader;
  private final OpenFgaServiceOptions options;

  public CheckHandler(TypeSystemResolver resolver, TupleReader reader, OpenFgaServiceOptions options) {
    this.resolver = resolver;
    this.reader = reader;
    this.options = options;
  }

  @Override
  Future<CheckResponse> execute(CheckRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> check(request.getStoreId(), request.getTupleKey(),
            request.getContextualTuples().getTupleKeysList(), request.getContext(), types))
        .map(allowed -> CheckResponse.newBuilder().setAllowed(allowed).build());
  }

  Future<Boolean> check(String store, CheckRequestTupleKey tuple, List<TupleKey> contextual, Struct context,
      TypeSystem types) {
    TupleKey key = TupleKey.newBuilder().setObject(tuple.getObject()).setRelation(tuple.getRelation())
        .setUser(tuple.getUser()).build();
    RelationshipTuple checked;
    try {
      checked = RelationshipTuple.parse(key);
      new KeyValidator(types).validate(checked);
    } catch (InvalidTupleException invalid) {
      throw new OpenFgaException(ErrorCode.validation_error, "invalid relation: " + invalid.getMessage());
    }
    try {
      new ContextValidator().validate(context);
    } catch (IllegalArgumentException invalid) {
      throw new OpenFgaException(ErrorCode.validation_error, "invalid context: " + invalid.getMessage());
    }
    WriteValidator validator = new WriteValidator(types);
    for (TupleKey tupleKey : contextual) {
      try {
        validator.validate(tupleKey);
      } catch (OpenFgaException invalid) {
        throw new OpenFgaException(ErrorCode.invalid_tuple, invalid.getMessage());
      }
    }
    LocalChecker checker = new LocalChecker(types, new CombinedTupleReader(reader, contextual),
        options.resolveNodeLimit());
    return checker.check(new ResolveCheckRequest(store, checked, context)).map(CheckResult::allowed);
  }
}
