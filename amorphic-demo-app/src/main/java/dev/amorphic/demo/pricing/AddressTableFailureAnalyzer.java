package dev.amorphic.demo.pricing;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Turns address-table errors into a short, readable startup failure instead of a stack trace. */
class AddressTableFailureAnalyzer extends AbstractFailureAnalyzer<AddressTableException> {
    @Override
    protected FailureAnalysis analyze(Throwable root, AddressTableException cause) {
        return new FailureAnalysis(cause.getMessage(),
                "Fix the address table (amorphic.addresses / AMORPHIC_ADDRESSES) or rebuild the app.", cause);
    }
}
