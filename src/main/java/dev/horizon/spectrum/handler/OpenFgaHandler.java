package dev.horizon.spectrum.handler;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import io.envoyproxy.pgv.ReflectiveValidatorIndex;
import io.envoyproxy.pgv.ValidationException;
import io.envoyproxy.pgv.ValidatorIndex;
import io.vertx.core.Handler;
import io.vertx.grpc.common.GrpcMessageDecoder;
import io.vertx.grpc.common.GrpcMessageEncoder;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerRequest;
import io.vertx.grpc.server.StatusException;

public abstract class OpenFgaHandler<Req extends Message, Resp extends Message>
    implements Handler<GrpcServerRequest<Req, Resp>> {

  public static final ServiceName SERVICE_NAME = ServiceName.create("openfga.v1", "OpenFGAService");

  static final String INTERNAL_ERROR = "Internal Server Error";

  private static final System.Logger LOGGER = System.getLogger(OpenFgaHandler.class.getName());
  private static final ValidatorIndex VALIDATORS = new ReflectiveValidatorIndex();

  static <Req extends Message, Resp extends Message> ServiceMethod<Req, Resp> method(String name,
      MessageOrBuilder request) {
    return ServiceMethod.server(SERVICE_NAME, name, GrpcMessageEncoder.encoder(), GrpcMessageDecoder.decoder(request));
  }

  void validate(Message request) {
    try {
      VALIDATORS.validatorFor(request).assertValid(request);
    } catch (ValidationException invalid) {
      throw new OpenFgaException(ErrorCode.validation_error, invalid.getMessage());
    }
  }

  StatusException status(Throwable failure) {
    return switch (failure) {
      case StatusException status -> status;
      case OpenFgaException openFga -> openFga.toStatus();
      default -> {
        LOGGER.log(System.Logger.Level.ERROR, "an OpenFGA call failed", failure);
        yield new StatusException(GrpcStatus.INTERNAL, INTERNAL_ERROR);
      }
    };
  }
}
