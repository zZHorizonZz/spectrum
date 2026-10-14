# Switch to an OpenFGA server

Spectrum and an OpenFGA server serve the same gRPC API, `openfga.v1.OpenFGAService`. When an application outgrows
Spectrum, run a standalone [OpenFGA](https://openfga.dev/docs/getting-started/setup-openfga/overview) server and point
the client at it. The code that calls the service stays the same.

## Before you begin

- Run an OpenFGA server that your application can reach. It serves gRPC on port `8081` by default.
- Add `io.vertx:vertx-grpc-client` to your dependencies.

## Point the client at the server

Replace the event bus client with a gRPC client of the server:

```java
OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(GrpcClient.client(vertx),
    SocketAddress.inetSocketAddress(8081, "openfga.internal"));
```

Every call of `fga` now goes to the OpenFGA server. Remove `OpenFgaService` from your gRPC server.

## Move the data

Spectrum and the OpenFGA server keep their data in their own databases. To move a store:

1. Create the store on the server with CreateStore.
2. Read each authorization model from Spectrum with ReadAuthorizationModels, and write it to the server with
   WriteAuthorizationModel, oldest first. The server gives each model a new ID.
3. Read the tuples from Spectrum with Read, page by page, and write them to the server with Write, at most 100 at a
   time.
4. Read the assertions of each model with ReadAssertions, and write them to the server with WriteAssertions, under
   the new model ID.

The changes of ReadChanges stay in Spectrum. The server records the writes of step 3 as new changes.

## Check what changes

- **Error status codes:** the server sends its error code as the gRPC status code, such as `2000` for
  `validation_error`, where Spectrum sends `INVALID_ARGUMENT`. Update code that reads the status of a failure. For
  more information, refer to [OpenFGA compatibility](../reference/openfga-compatibility.md#status-codes).
- **Continuation tokens:** a token of Spectrum does not work on the server. Start each list again after the switch.
- **IDs:** the server gives new store and model IDs. Update the IDs that your application keeps.

## Related resources

- [OpenFGA compatibility](../reference/openfga-compatibility.md)
- [Serve the service](serve-the-service.md)
