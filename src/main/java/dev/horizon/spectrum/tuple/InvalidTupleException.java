package dev.horizon.spectrum.tuple;

/**
 * A tuple, or a part of a tuple, that is malformed or does not fit an authorization model. The message is the reason.
 */
public final class InvalidTupleException extends RuntimeException {

  public InvalidTupleException(String reason) {
    super(reason, null, false, false);
  }
}
