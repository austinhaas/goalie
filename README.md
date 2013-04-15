# Goalie

This library can be used to unobtrusively instrument core.logic
goals. In short, you can specify functions to be called every time a
goal is entered and exited, and those functions will be passed a map
containing info about the current state of the search. The functions
are only used for their side-effects; they cannot alter the
search. This library includes a function that prints out the name of a
goal and its arguments each time it is called.

The design constraints are:

  - Don't require changes to core.logic.
  - Don't require changes to the library we want to instrument.
  - Keep it simple. Prove that this is a sound way to instrument core.logic. Don't assume too much about how it might be used.

### Example

    (ns example
      (:require
       [clojure.core.logic :refer (run* membero)]
       [pettomato.goalie :refer (with-traced-goals with-hooks)]
       [pettomato.goalie.print :refer (print-node)]))

    (with-traced-goals [membero conso]       ; The goals to instrument, e.g., show up in stacktraces.
      (with-hooks [membero]                  ; The goals to add hooks to.
        print-node                           ; Included hook; prints debug info.
        print-node

        (doall (run* [q] (membero q [1 2]))) ; Eval just this, if you don't need to trace.

        ))

    ;; This isn't a very good example, because core.logic doesn't define membero using conso,
    ;; and even if it did, this example wouldn't demonstrate any reason for conso to be traced.

### Example usage scenarios:

  - Log the current substitution every time a goal is invoked.

  - Log the values of the reified arguments to a goal at the time it
    was invoked, and every time it produces a result, then compare to
    report which logic variables took on new assignments during the
    computation of that goal.

  - Throw an error any time a goal is invoked with only nonground
    arguments, and include a stacktrace of the path from the offending
    node back up to the outermost instrumented goal.

  - Throw an error and print the path to the current goal when the
    program diverges. (You could count how many times a goal is
    constructed to detect possible divergence, for example.)

## Usage

Please refer to the examples in `src/com/pettomato/goalie/examples.clj`.

I wasn't sure that this would be useful; I suspect we need higher
level debugging aids, and they'd probably require support built into
core.logic's implementation, But I found myself using it a lot
(especially when I know what I'm looking for). I think its main virtue
is that it can be more effective than adding print statements. Another
valuable feature is triggering an error when a program diverges and
having access to the entire branch, in isolation. Finally, I've found
it helpful to inspect the shape of the search by selecting which goals
are instrumented.

## Caveats, Warnings, To do

Document the map that is passed to hooks.

Document the output from print-node, but I haven't settled on that.

When a goal on a branch fails, it short-ciruits and we lose the
ability to propagate information about the search. Currently, there is
a workaround in place so that we can fire hooks when a traced goal
fails, but the graph link to the previous goal points to the same node
as the parent, which is the entry point for the goal. So, at least we
can connect the output to the input in the case of failure, but you
can't walk back from one failed goal to a subordinate failed
goal. That's probably not an issue if you are printing everything as
it happens, or if you are tracing up from any non-failure node, but it
shows up in print-node because we display the id of a previous node so
that the user can follow the path of a branch obscured by interleaving
branches. Also, if you write any code that walks the graph we are
creating, you might encounter this.

The display of a search could be vastly improved. I'd love to export
to a graph library with better visualization and navigation
options. Currently, I use the occur feature in Emacs to manage level
of detail when there is too much output.

Instrumenting goals may affect the interleaving search of a logic
program. In other words, you may get answers in a different order when
instrumenting. I don't think this is a problem and I haven't
investigated it thoroughly, but we are meddling a bit with the
interleaving when we wrap result streams, so beware.

## License

Copyright © 2013 Pet Tomato, Inc. All Rights Reserved.

Distributed under the Eclipse Public License, the same as Clojure.
