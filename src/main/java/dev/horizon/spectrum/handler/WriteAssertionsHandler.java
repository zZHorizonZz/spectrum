package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.common.ContextValidator;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.model.TypeSystemResolver;
import dev.horizon.spectrum.openfga.v1.Assertion;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.WriteAssertionsRequest;
import dev.horizon.spectrum.openfga.v1.WriteAssertionsResponse;
import dev.horizon.spectrum.storage.AssertionsBackend;
import dev.horizon.spectrum.tuple.InvalidTupleException;
import dev.horizon.spectrum.tuple.KeyValidator;
import dev.horizon.spectrum.tuple.RelationshipTuple;
import dev.horizon.spectrum.tuple.WriteValidator;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class WriteAssertionsHandler extends UnaryHandler<WriteAssertionsRequest, WriteAssertionsResponse> {

  public static final ServiceMethod<WriteAssertionsRequest, WriteAssertionsResponse> SERVICE_METHOD = method(
      "WriteAssertions", WriteAssertionsRequest.newBuilder());

  private final TypeSystemResolver resolver;
  private final AssertionsBackend assertions;

  public WriteAssertionsHandler(TypeSystemResolver resolver, AssertionsBackend assertions) {
    this.resolver = resolver;
    this.assertions = assertions;
  }

  @Override
  Future<WriteAssertionsResponse> execute(WriteAssertionsRequest request) {
    return resolver.resolve(request.getStoreId(), request.getAuthorizationModelId())
        .compose(types -> execute(request, types));
  }

  private Future<WriteAssertionsResponse> execute(WriteAssertionsRequest request, TypeSystem types) {
    KeyValidator keys = new KeyValidator(types);
    WriteValidator writes = new WriteValidator(types);
    ContextValidator contexts = new ContextValidator();
    for (Assertion assertion : request.getAssertionsList()) {
      TupleKey key = TupleKey.newBuilder().setObject(assertion.getTupleKey().getObject())
          .setRelation(assertion.getTupleKey().getRelation()).setUser(assertion.getTupleKey().getUser()).build();
      try {
        keys.validate(RelationshipTuple.parse(key));
        contexts.validate(assertion.getContext());
      } catch (InvalidTupleException | IllegalArgumentException invalid) {
        throw new OpenFgaException(ErrorCode.validation_error, invalid.getMessage());
      }
      assertion.getContextualTuplesList().forEach(contextual -> writes.validate(contextual));
    }
    return assertions.write(request.getStoreId(), types.modelId(), request.getAssertionsList())
        .map(WriteAssertionsResponse.getDefaultInstance());
  }
}
