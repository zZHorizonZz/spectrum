package dev.horizon.spectrum;

import com.google.protobuf.ProtocolMessageEnum;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import dev.horizon.spectrum.openfga.v1.InternalErrorCode;
import dev.horizon.spectrum.openfga.v1.NotFoundErrorCode;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.server.StatusException;
import java.util.Optional;

/**
 * A failed call, with its error code. The kind of the code gives the gRPC status: an {@link ErrorCode} is {@code
 * INVALID_ARGUMENT}, a {@link NotFoundErrorCode} {@code NOT_FOUND} and an {@link InternalErrorCode} {@code INTERNAL}.
 */
public final class OpenFgaException extends RuntimeException {

  private final GrpcStatus status;
  private final ProtocolMessageEnum code;

  public OpenFgaException(ErrorCode code, String message) {
    this(GrpcStatus.INVALID_ARGUMENT, code, message);
  }

  public OpenFgaException(NotFoundErrorCode code, String message) {
    this(GrpcStatus.NOT_FOUND, code, message);
  }

  public OpenFgaException(InternalErrorCode code, String message) {
    this(GrpcStatus.INTERNAL, code, message);
  }

  public OpenFgaException(GrpcStatus status, String message) {
    this(status, null, message);
  }

  private OpenFgaException(GrpcStatus status, ProtocolMessageEnum code, String message) {
    super(message, null, false, false);
    this.status = status;
    this.code = code;
  }

  /**
   * Gives the error code of a request that is not valid.
   *
   * @return the code if it is an {@link ErrorCode}, or empty if the failure is not found, internal or only a status
   */
  public Optional<ErrorCode> inputError() {
    return code instanceof ErrorCode input ? Optional.of(input) : Optional.empty();
  }

  /**
   * Gives the failure as the status that ends a gRPC call.
   *
   * @return the status of the kind of the code, or the status that the failure was made with, and the message
   */
  public StatusException toStatus() {
    return new StatusException(status, getMessage());
  }
}
