package dev.horizon.spectrum;

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * The limits of an {@link OpenFgaService}, and the key of its continuation tokens. The limits default to those of the
 * OpenFGA server, and the key to a random one.
 *
 * @param maxTuplesPerWrite the most tuples that one Write writes and deletes
 * @param maxTypesPerAuthorizationModel the most type definitions that an authorization model has
 * @param maxAuthorizationModelSizeInBytes the largest serialized size of an authorization model
 * @param maxConditionContextSizeInBytes the largest serialized size of the condition context of a written tuple
 * @param resolveNodeLimit the deepest that a Check, ListObjects or ListUsers follows usersets and tuplesets
 * @param maxChecksPerBatchCheck the most checks of one BatchCheck
 * @param listObjectsMaxResults the most objects that ListObjects gives
 * @param listObjectsDeadline how long ListObjects searches before it gives the objects it found
 * @param listUsersMaxResults the most users that ListUsers gives
 * @param listUsersDeadline how long ListUsers searches before it gives the users it found
 * @param pageTokenKey the AES key that encrypts continuation tokens. Services that serve the same clients need the same
 * key, so that a token of one service is good at the others.
 */
public record OpenFgaServiceOptions(int maxTuplesPerWrite, int maxTypesPerAuthorizationModel,
    int maxAuthorizationModelSizeInBytes, int maxConditionContextSizeInBytes, int resolveNodeLimit,
    int maxChecksPerBatchCheck, int listObjectsMaxResults, Duration listObjectsDeadline, int listUsersMaxResults,
    Duration listUsersDeadline, SecretKey pageTokenKey) {

  private static final int DEFAULT_MAX_TUPLES_PER_WRITE = 100;
  private static final int DEFAULT_MAX_TYPES_PER_AUTHORIZATION_MODEL = 100;
  private static final int DEFAULT_MAX_AUTHORIZATION_MODEL_SIZE_IN_BYTES = 256 * 1024;
  private static final int DEFAULT_MAX_CONDITION_CONTEXT_SIZE_IN_BYTES = 32 * 1024;
  private static final int DEFAULT_RESOLVE_NODE_LIMIT = 25;
  private static final int DEFAULT_MAX_CHECKS_PER_BATCH_CHECK = 50;
  private static final int DEFAULT_LIST_MAX_RESULTS = 1000;
  private static final Duration DEFAULT_LIST_DEADLINE = Duration.ofSeconds(3);
  private static final int PAGE_TOKEN_KEY_BITS = 256;

  public OpenFgaServiceOptions {
    if (maxTuplesPerWrite < 1 || maxTypesPerAuthorizationModel < 1 || maxAuthorizationModelSizeInBytes < 1
        || maxConditionContextSizeInBytes < 1 || resolveNodeLimit < 1 || maxChecksPerBatchCheck < 1
        || listObjectsMaxResults < 1 || listUsersMaxResults < 1) {
      throw new IllegalArgumentException("every limit must be at least 1");
    }
    if (listObjectsDeadline.isNegative() || listObjectsDeadline.isZero() || listUsersDeadline.isNegative()
        || listUsersDeadline.isZero()) {
      throw new IllegalArgumentException("every deadline must be positive");
    }
    Objects.requireNonNull(pageTokenKey, "pageTokenKey");
  }

  public OpenFgaServiceOptions() {
    this(DEFAULT_MAX_TUPLES_PER_WRITE, DEFAULT_MAX_TYPES_PER_AUTHORIZATION_MODEL,
        DEFAULT_MAX_AUTHORIZATION_MODEL_SIZE_IN_BYTES, DEFAULT_MAX_CONDITION_CONTEXT_SIZE_IN_BYTES,
        DEFAULT_RESOLVE_NODE_LIMIT, DEFAULT_MAX_CHECKS_PER_BATCH_CHECK, DEFAULT_LIST_MAX_RESULTS,
        DEFAULT_LIST_DEADLINE, DEFAULT_LIST_MAX_RESULTS, DEFAULT_LIST_DEADLINE, randomKey());
  }

  /**
   * Gives the options with another limit of the tuples that one Write writes and deletes.
   *
   * @param value the most tuples, at least 1
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withMaxTuplesPerWrite(int value) {
    return new OpenFgaServiceOptions(value, maxTypesPerAuthorizationModel, maxAuthorizationModelSizeInBytes,
        maxConditionContextSizeInBytes, resolveNodeLimit, maxChecksPerBatchCheck, listObjectsMaxResults,
        listObjectsDeadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another limit of the type definitions of an authorization model.
   *
   * @param value the most type definitions, at least 1
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withMaxTypesPerAuthorizationModel(int value) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, value, maxAuthorizationModelSizeInBytes,
        maxConditionContextSizeInBytes, resolveNodeLimit, maxChecksPerBatchCheck, listObjectsMaxResults,
        listObjectsDeadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another limit of the serialized size of an authorization model.
   *
   * @param value the largest size in bytes, at least 1
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withMaxAuthorizationModelSizeInBytes(int value) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel, value,
        maxConditionContextSizeInBytes, resolveNodeLimit, maxChecksPerBatchCheck, listObjectsMaxResults,
        listObjectsDeadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another limit of the serialized size of the condition context of a tuple.
   *
   * @param value the largest size in bytes, at least 1
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withMaxConditionContextSizeInBytes(int value) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel,
        maxAuthorizationModelSizeInBytes, value, resolveNodeLimit, maxChecksPerBatchCheck, listObjectsMaxResults,
        listObjectsDeadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another depth that a Check, ListObjects or ListUsers follows usersets and tuplesets to.
   *
   * @param value the deepest dispatch, at least 1
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withResolveNodeLimit(int value) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel,
        maxAuthorizationModelSizeInBytes, maxConditionContextSizeInBytes, value, maxChecksPerBatchCheck,
        listObjectsMaxResults, listObjectsDeadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another limit of the checks of one BatchCheck.
   *
   * @param value the most checks, at least 1
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withMaxChecksPerBatchCheck(int value) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel,
        maxAuthorizationModelSizeInBytes, maxConditionContextSizeInBytes, resolveNodeLimit, value,
        listObjectsMaxResults, listObjectsDeadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another limit and deadline of ListObjects.
   *
   * @param maxResults the most objects that ListObjects gives, at least 1
   * @param deadline how long ListObjects searches before it gives the objects it found, more than zero
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withListObjects(int maxResults, Duration deadline) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel,
        maxAuthorizationModelSizeInBytes, maxConditionContextSizeInBytes, resolveNodeLimit, maxChecksPerBatchCheck,
        maxResults, deadline, listUsersMaxResults, listUsersDeadline, pageTokenKey);
  }

  /**
   * Gives the options with another limit and deadline of ListUsers.
   *
   * @param maxResults the most users that ListUsers gives, at least 1
   * @param deadline how long ListUsers searches before it gives the users it found, more than zero
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withListUsers(int maxResults, Duration deadline) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel,
        maxAuthorizationModelSizeInBytes, maxConditionContextSizeInBytes, resolveNodeLimit, maxChecksPerBatchCheck,
        listObjectsMaxResults, listObjectsDeadline, maxResults, deadline, pageTokenKey);
  }

  /**
   * Gives the options with another key of the continuation tokens.
   *
   * @param key the AES key of 128, 192 or 256 bits that encrypts continuation tokens
   * @return the options, with the other values unchanged
   */
  public OpenFgaServiceOptions withPageTokenKey(SecretKey key) {
    return new OpenFgaServiceOptions(maxTuplesPerWrite, maxTypesPerAuthorizationModel,
        maxAuthorizationModelSizeInBytes, maxConditionContextSizeInBytes, resolveNodeLimit, maxChecksPerBatchCheck,
        listObjectsMaxResults, listObjectsDeadline, listUsersMaxResults, listUsersDeadline, key);
  }

  private static SecretKey randomKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance("AES");
      generator.init(PAGE_TOKEN_KEY_BITS);
      return generator.generateKey();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("every Java platform has AES", impossible);
    }
  }
}
