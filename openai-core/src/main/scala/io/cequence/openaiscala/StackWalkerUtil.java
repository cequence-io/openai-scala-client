package io.cequence.openaiscala;

import java.util.Optional;
import java.util.function.Predicate;

// this requires Java 9+
public class StackWalkerUtil {

  private static StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  public static Optional<String> functionName(
    int skip,
    Optional<Predicate<String>> predicate
  ) {
    return walker.walk(frames -> frames
            .map(StackWalker.StackFrame::getMethodName)
            .skip(skip)
            .filter(predicate.orElse(s -> true))
            .findFirst()
    );
  }
}
