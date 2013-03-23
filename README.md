# Goalie

This library can be used to unobtrusively instrument core.logic
goals. In short, you can specify a set of goals to be traced and pairs
of functions to be called upon entering and exiting designated goals.

Example usage scenarios:

  - Log the current substitution every time a goal is invoked.

  - Log the values of the reified arguments to a goal at the time it
    was invoked, and every time it produces a result, then compare to
    report which logic variables took on new assignments during the
    computation of that goal.

  - Throw an error anytime a goal is invoked with only unground
    arguments, and include a stacktrace of the path from the offending
    node back up to the outermost instrumented goal.

## Usage

Please refer to the examples in `src/com/pettomato/goalie/examples.clj`.

## License

Copyright © 2013 Pet Tomato, Inc. All Rights Reserved.

Distributed under the Eclipse Public License, the same as Clojure.
