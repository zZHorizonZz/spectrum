package dev.horizon.spectrum.common;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelBuilder;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOptions;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelValidationException;
import dev.cel.common.types.OpaqueType;
import dev.cel.common.types.SimpleType;
import dev.cel.parser.CelStandardMacro;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntime;
import dev.horizon.spectrum.OpenFgaException;
import dev.horizon.spectrum.openfga.v1.Condition;
import dev.horizon.spectrum.openfga.v1.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A condition of an authorization model: a CEL expression of typed parameters, which a tuple with the condition must
 * meet to count. The context of a request and the context of the tuple give the parameters, and the tuple's context
 * wins when both have one.
 */
public final class EvaluableCondition {

  private static final int MAX_COMPREHENSION_ITERATIONS = 100;

  private final Condition condition;
  private volatile Compiled compiled;

  private record Compiled(Map<String, ParameterType> parameters, CelRuntime.Program program) {
  }

  public EvaluableCondition(Condition condition) {
    this.condition = condition;
  }

  public String name() {
    return condition.getName();
  }

  /**
   * Compiles the expression, once.
   *
   * @throws IllegalArgumentException if a parameter type is not valid, or the expression does not compile to a {@code
   * bool}
   */
  public void compile() {
    compiled();
  }

  /**
   * Gives the values of a context that are parameters of the condition, as CEL values.
   *
   * @param context the fields of the context
   * @return the values, by parameter name, without the fields that are not parameters
   * @throws IllegalArgumentException if a value is not one of the type of its parameter
   */
  public Map<String, Object> typed(Map<String, Value> context) {
    if (!context.isEmpty() && condition.getParametersCount() == 0) {
      throw new IllegalArgumentException("parameter type error on condition '" + name()
          + "' - no parameters defined for the condition");
    }
    Map<String, Object> typed = new LinkedHashMap<>();
    compiled().parameters().forEach((parameter, type) -> {
      Value value = context.get(parameter);
      if (value != null) {
        try {
          typed.put(parameter, type.convert(value));
        } catch (IllegalArgumentException | ArithmeticException invalid) {
          throw new IllegalArgumentException("parameter type error on condition '" + name()
              + "' - failed to convert context parameter '" + parameter + "': " + invalid.getMessage(), invalid);
        }
      }
    });
    return typed;
  }

  /**
   * Tells if a tuple meets the condition.
   *
   * @param tuple the tuple, whose condition is this one, for the message of a failure
   * @param written the context that the tuple was written with
   * @param context the context of the request, or the empty struct
   * @return true if the expression is true for the merged context
   * @throws OpenFgaException if the merged context misses a parameter, a value is not one of its type, or the
   * expression fails
   */
  public boolean evaluate(String tuple, Struct written, Struct context) {
    Map<String, Value> merged = new LinkedHashMap<>(context.getFieldsMap());
    merged.putAll(written.getFieldsMap());
    Map<String, Object> values;
    try {
      values = typed(merged);
    } catch (IllegalArgumentException invalid) {
      throw failure(invalid.getMessage());
    }
    TreeSet<String> missing = new TreeSet<>(condition.getParametersMap().keySet());
    missing.removeAll(values.keySet());
    if (!missing.isEmpty()) {
      throw failure("tuple '" + tuple + "' is missing context parameters '" + List.copyOf(missing)
          + "'");
    }
    try {
      return Boolean.TRUE.equals(compiled().program().eval(values));
    } catch (CelEvaluationException failed) {
      throw failure("'" + name() + "' - failed to evaluate condition expression: " + failed.getMessage());
    }
  }

  private OpenFgaException failure(String cause) {
    return new OpenFgaException(ErrorCode.validation_error, "failed to evaluate relationship condition: " + cause);
  }

  private Compiled compiled() {
    Compiled done = compiled;
    if (done == null) {
      synchronized (this) {
        done = compiled;
        if (done == null) {
          done = compileNow();
          compiled = done;
        }
      }
    }
    return done;
  }

  private Compiled compileNow() {
    Map<String, ParameterType> parameters = new LinkedHashMap<>();
    condition.getParametersMap().forEach((parameter, reference) -> {
      try {
        parameters.put(parameter, ParameterType.of(reference));
      } catch (IllegalArgumentException invalid) {
        throw compilationError("failed to decode parameter type for parameter '" + parameter + "': "
            + invalid.getMessage());
      }
    });
    OpaqueType ipAddress = IpAddressParameterType.CEL_TYPE;
    CelBuilder builder = CelFactory.standardCelBuilder()
        .setOptions(CelOptions.current().comprehensionMaxIterations(MAX_COMPREHENSION_ITERATIONS).build())
        .setStandardMacros(CelStandardMacro.STANDARD_MACROS)
        .addFunctionDeclarations(
            CelFunctionDecl.newFunctionDeclaration("ipaddress",
                CelOverloadDecl.newGlobalOverload("string_to_ipaddress", ipAddress, SimpleType.STRING)),
            CelFunctionDecl.newFunctionDeclaration("in_cidr",
                CelOverloadDecl.newMemberOverload("ipaddr_in_cidr", SimpleType.BOOL, ipAddress, SimpleType.STRING)))
        .addFunctionBindings(
            CelFunctionBinding.from("string_to_ipaddress", String.class, IpAddress::new),
            CelFunctionBinding.from("ipaddr_in_cidr", IpAddress.class, String.class, IpAddress::inCidr))
        .setResultType(SimpleType.BOOL);
    parameters.forEach((parameter, type) -> builder.addVar(parameter, type.celType()));
    Cel cel = builder.build();
    try {
      CelAbstractSyntaxTree ast = cel.compile(condition.getExpression()).getAst();
      return new Compiled(parameters, cel.createProgram(ast));
    } catch (CelValidationException invalid) {
      throw compilationError(invalid.getMessage());
    } catch (CelEvaluationException invalid) {
      throw compilationError("condition expression construction: " + invalid.getMessage());
    }
  }

  private IllegalArgumentException compilationError(String cause) {
    return new IllegalArgumentException("failed to compile expression on condition '" + name() + "' - " + cause);
  }
}
