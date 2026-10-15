package dev.horizon.spectrum.handler;

import com.google.protobuf.Message;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.GrpcServerRequest;

public abstract class UnaryHandler<Req extends Message, Resp extends Message> extends OpenFgaHandler<Req, Resp> {

  @Override
  public void handle(GrpcServerRequest<Req, Resp> request) {
    request.handler(message -> {
      Context context = Vertx.currentContext();
      call(message).onComplete(done -> context.runOnContext(end -> {
        if (done.succeeded()) {
          request.response().end(done.result());
        } else {
          request.response().fail(done.cause());
        }
      }));
    });
  }

  abstract Future<Resp> execute(Req request);

  final Future<Resp> call(Req request) {
    Future<Resp> result;
    try {
      validate(request);
      result = execute(request);
    } catch (RuntimeException failed) {
      result = Future.failedFuture(failed);
    }
    return result.recover(failed -> Future.failedFuture(status(failed)));
  }
}
