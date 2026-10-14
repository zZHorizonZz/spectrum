# Limits

These limits are fixed in the source of Spectrum 0.1.0. You cannot configure them. For how depth works, refer to
[Check evaluation](../concepts/check-evaluation.md).

Reviewed: 2026-10-14

| Limit                                   | Value                                                  |
| --------------------------------------- | ------------------------------------------------------ |
| Length of an object or a subject        | 255 characters                                         |
| Length of a type or a relation          | 64 characters                                          |
| Characters of a type or a relation      | `a` to `z`, `0` to `9` and `_`, starting with a letter |
| Characters of an identifier             | Any character except whitespace and `#`                |
| Store id                                | 1 to 32 letters, digits, `_` and `-`                   |
| Length of a store name                  | 1 to 255 characters                                    |
| Depth of a check or a list              | 25 levels                                              |
| Relationships per insert statement      | 400                                                    |
| Changes per insert statement            | 300                                                    |
| Subjects per read of a list of objects  | 500                                                    |
| Tuples per OpenFGA `Write`              | 100                                                    |
| Assertions per model in the OpenFGA API | 100                                                    |
| Page size of the OpenFGA API            | 1 to 100, 50 by default                                |

## Statement sizes

`write` of a list inserts 400 relationships per statement, which uses 1,600 placeholders: the store, object, relation
and subject of each. SQL Server allows at most 2,100 placeholders in one statement. A list of any length still runs
in one transaction.
