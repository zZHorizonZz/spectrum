# Changelog

## [0.2.1](https://github.com/zZHorizonZz/spectrum/compare/v0.2.0...v0.2.1) (2026-10-25)


### Fixes

* depend on aquery 0.3.0, basepoint 0.3.1 and cinder 0.4.1, none of which needs a snapshot. ([b203c09](https://github.com/zZHorizonZz/spectrum/commit/b203c0971f45a25e499c5e57d327aa5cd73a62b5))
* run on cinder 0.4.0, and open the table messages to protobuf, whose field accessors read them reflectively. ([4023615](https://github.com/zZHorizonZz/spectrum/commit/402361541a89971a66e1bb029c5a3b7c78f3afaa))

## [0.2.0](https://github.com/zZHorizonZz/spectrum/compare/v0.1.0...v0.2.0) (2026-10-23)


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
