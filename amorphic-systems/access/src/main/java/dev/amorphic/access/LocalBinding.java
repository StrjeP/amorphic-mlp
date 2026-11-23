package dev.amorphic.access;

import dev.amorphic.addressing.Address;
import dev.amorphic.envelope.Envelope;
import java.util.Objects;

/** The local binding: hands the envelope to a handler in the same process. */
public final class LocalBinding implements Invoker {

    private final Handler handler;

    public LocalBinding(Handler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Override
    public Reply invoke(Address address, Envelope request) {
        long start = System.nanoTime();
        Envelope reply = handler.handle(request);
        return new Reply(reply, Timing.of(start));
    }
}
