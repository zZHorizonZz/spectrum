# Conditions

A condition is a [CEL](https://cel.dev) expression that a tuple can carry. The tuple counts only while the expression
is true. OpenFGA evaluates conditions with cel-go, and Spectrum evaluates them with
[cel-java](https://github.com/google/cel-java), the Java implementation of the same language.

## How a condition works

1. The authorization model declares the condition: its name, its expression, and the type of each parameter.

   ```json
   "conditions": {
     "in_network": {
       "name": "in_network",
       "expression": "ip.in_cidr(network)",
       "parameters": {
         "ip": { "type_name": "TYPE_NAME_IPADDRESS" },
         "network": { "type_name": "TYPE_NAME_STRING" }
       }
     }
   }
   ```

2. A type restriction of a relation allows tuples with the condition:

   ```json
   "viewer": { "directly_related_user_types": [{ "type": "user", "condition": "in_network" }] }
   ```

3. A tuple names the condition, and can give some of the parameters in its context:

   ```json
   {
     "user": "user:anne", "relation": "viewer", "object": "doc:1",
     "condition": { "name": "in_network", "context": { "network": "10.0.0.0/8" } }
   }
   ```

4. A Check, ListObjects or ListUsers request gives the rest of the parameters in its `context`:

   ```json
   { "context": { "ip": "10.1.2.3" } }
   ```

When the service reads the tuple, it merges the context of the request with the context of the tuple. The value of
the tuple wins when both have a parameter. The tuple counts if the expression is true for the merged context.

## Missing and invalid parameters

- **A missing parameter:** if the merged context misses a parameter of the condition, the evaluation fails. A check
  then fails with `INVALID_ARGUMENT` and names the missing parameters. When another tuple gives the relation, the
  check allows, as in OpenFGA.
- **A value of another type:** a value that is not one of the type of its parameter fails the evaluation.
- **A write:** a written tuple must name a condition of the model that its type restriction allows. Each value of
  its context must be of the type of its parameter.

## Parameter types

| Type name             | CEL type                    | Context value                                                                    |
| --------------------- | --------------------------- | -------------------------------------------------------------------------------- |
| `TYPE_NAME_BOOL`      | `bool`                      | A boolean                                                                        |
| `TYPE_NAME_STRING`    | `string`                    | A string                                                                         |
| `TYPE_NAME_INT`       | `int`                       | A whole number, or a string of one                                               |
| `TYPE_NAME_UINT`      | `uint`                      | A whole number that is not negative, or a string of one                          |
| `TYPE_NAME_DOUBLE`    | `double`                    | A number, or a string of one, within the range of a double                       |
| `TYPE_NAME_DURATION`  | `google.protobuf.Duration`  | A Go duration string, such as `1h30m` or `500ms`                                 |
| `TYPE_NAME_TIMESTAMP` | `google.protobuf.Timestamp` | An RFC 3339 string, such as `2026-10-15T08:00:00Z`                               |
| `TYPE_NAME_IPADDRESS` | `IPAddress`                 | An IPv4 address of four decimal octets without leading zeros, or an IPv6 address |
| `TYPE_NAME_MAP`       | `map(string, T)`            | An object whose values are of the generic type                                   |
| `TYPE_NAME_LIST`      | `list(T)`                   | An array whose values are of the generic type                                    |
| `TYPE_NAME_ANY`       | `dyn`                       | Any value                                                                        |

An `IPAddress` has the method `in_cidr`, such as `ip.in_cidr("10.0.0.0/8")`. The function `ipaddress` turns a
string into an address, such as `ipaddress("10.1.2.3")`.

## Limits

- **Expression size:** an expression has at most 512 bytes, as OpenFGA's protos allow.
- **Evaluation cost:** cel-java has no limit on the cost of an evaluation, as cel-go has. Instead, a comprehension,
  such as `all` or `exists`, runs at most 100 iterations.
- **Inline conditions:** Spectrum does not have the `$expression` conditions of OpenFGA, whose tuples carry their own
  expression.
