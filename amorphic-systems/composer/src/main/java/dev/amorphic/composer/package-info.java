/**
 * Composition (spec 04): a caller asks for an outcome, not a capability. A {@link dev.amorphic.composer.Recipe} says
 * how to reach an outcome as addressed steps; the {@link dev.amorphic.composer.Composer} applies it to one request,
 * chooses every step's route once from the context's {@link dev.amorphic.composer.Router}, invokes each step, and
 * records the plan's topology. Steps may fan out over a list, passing through items that need no call.
 */
package dev.amorphic.composer;
