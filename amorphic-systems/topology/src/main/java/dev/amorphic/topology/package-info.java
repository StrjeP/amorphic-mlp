/**
 * Topology (spec 03): what each call actually ran on. A {@link dev.amorphic.topology.Topology} is the resolved shape
 * (its roles, each with the instance that answered, and the caller), identified by a hash of its content and
 * addressed as {@code pkg:topology/<name>?hash=<hash>}. A {@link dev.amorphic.topology.CallRecord} names a call's
 * envelope and its topology. {@link dev.amorphic.topology.TopologyLog} appends both to a file and never changes them.
 */
package dev.amorphic.topology;
