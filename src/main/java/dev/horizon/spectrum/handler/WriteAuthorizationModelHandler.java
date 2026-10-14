package dev.horizon.spectrum.handler;

import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.OpenFgaServiceOptions;
import dev.horizon.spectrum.common.ULID;
import dev.horizon.spectrum.model.ModelValidator;
import dev.horizon.spectrum.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.AuthorizationModel;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelRequest;
import dev.horizon.spectrum.openfga.v1.WriteAuthorizationModelResponse;
import dev.horizon.spectrum.storage.AuthorizationModelBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class WriteAuthorizationModelHandler
    extends UnaryHandler<WriteAuthorizationModelRequest, WriteAuthorizationModelResponse> {

  public static final ServiceMethod<WriteAuthorizationModelRequest, WriteAuthorizationModelResponse> SERVICE_METHOD = method(
      "WriteAuthorizationModel", WriteAuthorizationModelRequest.newBuilder());

  private final AuthorizationModelBackend models;
  private final OpenFgaServiceOptions options;

  public WriteAuthorizationModelHandler(AuthorizationModelBackend models, OpenFgaServiceOptions options) {
    this.models = models;
    this.options = options;
  }

  @Override
  Future<WriteAuthorizationModelResponse> execute(WriteAuthorizationModelRequest request) {
    if (request.getTypeDefinitionsCount() > options.maxTypesPerAuthorizationModel()) {
      throw new OpenFgaException(ErrorCode.exceeded_entity_limit, "The number of type definitions in an"
          + " authorization model exceeds the allowed limit of " + options.maxTypesPerAuthorizationModel());
    }
    AuthorizationModel model = AuthorizationModel.newBuilder().setId(ULID.randomULID().toString())
        .setSchemaVersion(request.getSchemaVersion().isEmpty() ? TypeSystem.SCHEMA_VERSION_1_1
            : request.getSchemaVersion())
        .addAllTypeDefinitions(request.getTypeDefinitionsList()).putAllConditions(request.getConditionsMap()).build();
    int size = model.getSerializedSize();
    if (size > options.maxAuthorizationModelSizeInBytes()) {
      throw new OpenFgaException(ErrorCode.exceeded_entity_limit, "model exceeds size limit: " + size + " bytes vs "
          + options.maxAuthorizationModelSizeInBytes() + " bytes");
    }
    new ModelValidator(new TypeSystem(model)).validate();
    return models.write(request.getStoreId(), model)
        .map(WriteAuthorizationModelResponse.newBuilder().setAuthorizationModelId(model.getId()).build());
  }
}
