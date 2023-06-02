package io.cequence.openaiscala;

import java.util.Optional;

// this requires Java 9+
public class StackWalkerUtil {

  private static StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

  public static Optional<String> functionName(int skip) {
    return walker.walk(frames -> frames
            .map(StackWalker.StackFrame::getMethodName)
            .skip(skip)
            .findFirst());
  }
}
