package dev.horizon.spectrum.check;

/**
 * The answer of a check.
 *
 * @param allowed true if the user has the relation
 * @param cycleDetected true if a path of the check went back to a tuple it had checked. A path with a cycle does not
 * allow.
 */
public record CheckResult(boolean allowed, boolean cycleDetected) {

  static final CheckResult ALLOWED = new CheckResult(true, false);

  static final CheckResult DENIED = new CheckResult(false, false);

  static final CheckResult CYCLE = new CheckResult(false, true);
}
