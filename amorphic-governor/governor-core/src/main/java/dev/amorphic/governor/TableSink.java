package dev.amorphic.governor;

/**
 * Where an approved table goes. In-process it applies the table to the app directly; as a separate
 * service it would POST to the app's /admin/address-table.
 */
@FunctionalInterface
public interface TableSink {

    Result apply(String table, String source);

    sealed interface Result permits Accepted, Refused {}

    record Accepted(long generation) implements Result {}

    record Refused(String reason) implements Result {}
}
