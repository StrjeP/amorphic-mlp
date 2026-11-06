# The story: one call, many homes

This is the high-level account of how the demo's stages connect. For the build detail, see [amorphic-mlp-plan.md](amorphic-mlp-plan.md).

The demo app has one line of code that matters: `PricingCalc.convert(...)`. That line never changes. What changes is where the call goes, and that is decided by a small address table that a person edits between stages. The demo moves the call's home from one place to another, one cautious step at a time. Evidence from each step is what allows the next one.

## Stage 0: Baseline

Pricing is done by a Python service. It works, it is trusted, and the app calls it over HTTP. This is the starting point everything else is measured against.

## Stage 1: Shadow

A Java rewrite arrives (v2.0). Nobody trusts it yet, so it serves no one. The app keeps returning Python's answer, but it also sends every request to Java in the background and compares the two results.

Within minutes the comparison finds a problem. On amounts that land exactly on a half penny, Java rounds up and Python rounds to even. The gate script reads the evidence and refuses to pass. Without the shadow, this bug would have reached customers.

## Stage 1b: Fix

The mismatch the shadow caught, recorded in `/evidence`, is turned into a regression test: the same input, with Python's answer as the expected result. Run against v2.0, the test fails, which confirms it describes the bug. Java's rounding is then corrected, and in v2.1 the test passes.

v2.1 goes back into the shadow slot. Thousands of answers now match in a row and the gate passes. The evidence that found the bug is now part of the build, so the bug can't quietly return. Java has proven it gives the same answers, but it hasn't served a real customer yet.

## Stage 2: Canary

Java starts serving real traffic, first 10% of calls and then half. On those calls Python is still asked for its answer, so every call is still compared. Python remains the champion and stays available as the fallback. This stage tests whether Java holds up under real traffic, not just whether its answers are correct.

## Stage 3: Promote

The roles flip. Java becomes the champion and Python becomes the shadow. The comparison keeps running in the other direction, which confirms that nothing broke in the handover. Python is now the one being checked.

## Stage 3b: Deprecate

With enough matched evidence behind it, Python is switched off. Java is the only implementation. The capability has moved languages without the app's code changing at all.

## Stage 4: Fold-in

The last move changes the shape of the system. The Java logic was built as a plain library, so the app is rebuilt with that library inside it. The address table now says `local` instead of giving a URL, and the separate Java service disappears. The call that once crossed the network to Python is now an ordinary method call inside the app, and the evidence log shows the `local` binding.

## What ties it together

- **The call site stays fixed and the address moves.** Each stage is a different address table behind the same line of code.
- **Trust is earned in order.** Java's answers are compared before they are served (shadow), then Java serves a share of traffic (canary), then all of it (champion), and finally it runs in-process (local). Each step relies on evidence from the one before.
- **The planted bug is the point.** When stage 1 fails, the demo is working. The process catches a small, plausible difference that code review would likely miss, and the fix comes with a test so the lesson stays learned.
- **A person decides and the evidence informs.** Nothing advances automatically. A human reads the gate's result and edits the table, which keeps the MLP simple and keeps responsibility visible.
