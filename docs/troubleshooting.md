# Troubleshooting Spectrum

## A call fails with INVALID_ARGUMENT

The request breaks a rule of OpenFGA. The message of the status names the rule.

- **A field that does not match a pattern**, such as `.openfga.v1.CheckRequest.store_id: must match pattern`: the
  request breaks a `validate.rules` of OpenFGA's protos. A store ID and a model ID are ULIDs of 26 characters.
- **`No authorization models found for store`**: the store has no model yet. Write one with WriteAuthorizationModel.
- **`Invalid tuple ... Reason: type 'user' is not an allowed type restriction`**: the model does not allow the user
  of the tuple. Add the type to the `directly_related_user_types` of the relation.
- **`missing context parameters`**: a tuple has a condition, and neither the request nor the tuple gives every
  parameter of it. Add the missing parameters to the `context` of the request.

## A call fails with NOT_FOUND

The store does not exist, or it is deleted.

## A write fails with ABORTED

Another transaction changed the same tuples, or the store has the tuple with another condition. Read the tuple, and
write again.

## A call fails with INTERNAL

The service did not expect the failure, such as a lost connection to the database. The status gives no details. The
log of the service has them, under the logger `dev.horizon.spectrum.handler.OpenFgaHandler`.

## ListObjects or ListUsers gives fewer results than expected

The search reached its deadline, 3 seconds by default, and gave what it found so far. A slow database makes this
more likely. On MariaDB, set `setCachePreparedStatements(true)` on `MySQLConnectOptions`, so that a query takes one
round trip. For more information, refer to [Databases](reference/databases.md). To give the search more time, refer to
[Options](reference/options.md).

## A continuation token fails with INVALID_ARGUMENT

The token is from another list, or a service with another key made it. Give every service the same `pageTokenKey`,
and start the list again. For more information, refer to [Options](reference/options.md).

## A call fails with UNIMPLEMENTED

The server does not have the service. Add `OpenFgaService` to the server that the client calls. For more
information, refer to [Serve the service](how-to/serve-the-service.md).
