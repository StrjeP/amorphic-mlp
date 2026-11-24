package dev.amorphic.demo.pricing;

/** Lets tests outside this package use the stand-in for /meta. */
public final class StageAddressTablesTestAccess {

    public static final MetaReader DECLARED_META = StageAddressTablesTest.DECLARED_META;

    private StageAddressTablesTestAccess() {}
}
