# Serve the OpenFGA API over gRPC and HTTP

Spectrum ships no server process. To reach the OpenFGA service from other processes, add it to a Vert.x gRPC server in
your own application. One port then serves both gRPC and OpenFGA's HTTP API with JSON.

## Before you begin

- Add `spectrum-openfga` to your dependencies. For more information, refer to
  [Use the OpenFGA API](use-the-openfga-api.md).
- Create the tables. For more information, refer to [Create the tables](create-the-tables.md).

## Serve the API

1. Create a gRPC server, and add the service to it:

   ```java
   GrpcServer grpc = GrpcServer.server(vertx);
   grpc.addService(OpenFGAServiceGrpcService.of(new OpenFgaService(pool, dialect)));
   ```

2. Start an HTTP server with the gRPC server as its request handler:

   ```java
   vertx.createHttpServer()
       .requestHandler(grpc)
       .listen(8080)
       .await();
   ```

   The server takes gRPC calls, and HTTP requests on the paths of OpenFGA's API, such as
   `POST /stores/{store_id}/check`. Vert.x transcodes the HTTP requests from the `google.api.http` options of the
   OpenFGA protos.

> [!NOTE]
> Transcoding is a tech preview in Vert.x 5.2. Every HTTP request, also a `GET`, must have the header
> `Content-Type: application/json`. Without it, the server answers `415 Unsupported Media Type`.

## Call the API from another process

- **gRPC:** Create a Vert.x gRPC client for the address of the server:

  ```java
  OpenFGAServiceGrpcClient fga = OpenFGAServiceGrpcClient.create(GrpcClient.client(vertx),
      SocketAddress.inetSocketAddress(8080, "localhost"));
  ```

- **HTTP:** Send JSON to the paths of OpenFGA's API:

  ```sh
  curl -X POST http://localhost:8080/stores -H 'Content-Type: application/json' -d '{"name":"documents"}'
  ```

## Verify

Create a store over HTTP. The response holds its id:

```sh
curl -X POST http://localhost:8080/stores -H 'Content-Type: application/json' -d '{"name":"documents"}'
```

```txt output
{"id":"01M4XNYW5SK4DFCPRPE7SHW5H0","name":"documents","created_at":"2026-10-14T17:04:33.056Z","updated_at":"2026-10-14T17:04:33.056Z"}
```

## Next steps

- [OpenFGA compatibility](../reference/openfga-compatibility.md)
- [Troubleshooting Spectrum](../troubleshooting.md)
