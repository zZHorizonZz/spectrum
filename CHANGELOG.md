# Changelog

## [0.2.0](https://github.com/zZHorizonZz/spectrum/compare/v0.1.0...v0.2.0) (2026-10-16)


### ⚠ BREAKING CHANGES

* OpenFgaService.create takes the Cinder Driver of the database, such as new PostgresDriver(), in place of a Basepoint Dialect.

### Refactoring

* read and write the tables through Cinder backends. ([7d6e85f](https://github.com/zZHorizonZz/spectrum/commit/7d6e85fbbb2ecc6acc3b69dc04af4b1ff696f9f5))

## 0.1.0 (2026-10-15)


### Features

* prefix the tables and the temporary objects of the migrations with spectrum_ ([d2bfc41](https://github.com/zZHorizonZz/spectrum/commit/d2bfc4102a67a38b532917d2e57098a838ae6c40))
* serve the OpenFGA service on Vert.x from SQL databases ([76d5c89](https://github.com/zZHorizonZz/spectrum/commit/76d5c893cb8215189878a773cbed09e38bb8f2e7))


### Fixes

* write and end each response on the context of its request ([d501a26](https://github.com/zZHorizonZz/spectrum/commit/d501a26497a233b669b82f18029710363ca08eef))


### Refactoring

* deliver streamed objects on the context of the caller, without a flag in the handler ([59d6d46](https://github.com/zZHorizonZz/spectrum/commit/59d6d46eeccc4025aab19789569a247b59865274))
