package dev.amorphic.demo.compose;

import com.fasterxml.jackson.databind.JsonNode;
import dev.amorphic.access.Reply;
import dev.amorphic.addressing.Address;
import dev.amorphic.composer.Composer;
import dev.amorphic.composer.Run;
import dev.amorphic.demo.evidence.EvidenceRecorder;
import dev.amorphic.demo.pricing.ReloadableRouting;
import dev.amorphic.envelope.Aspect;
import dev.amorphic.envelope.Envelope;
import dev.amorphic.envelope.InstanceRef;
import java.math.BigDecimal;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the app's endpoints ask for what they want (spec 04). Each asks for an outcome; the composer works out the
 * plan and runs it. Once a plan's comparisons are in, its pricing calls and its shadow result go to evidence.
 */
public class Outcomes {

    private static final Logger LOG = LoggerFactory.getLogger(Outcomes.class);

    public static final Address QUOTE = Address.parse("pkg:outcome/quote@1.0");
    public static final Address ACCOUNT_VALUE = Address.parse("pkg:outcome/account-value@1.0");
    static final Address QUOTE_REQUEST = Address.parse("pkg:schema/quote-request@1.0");
    static final Address ACCOUNT_VALUE_REQUEST = Address.parse("pkg:schema/account-value-request@1.0");

    private final Composer composer;
    private final EvidenceRecorder evidence;
    private final Clock clock;

    public Outcomes(Composer composer, EvidenceRecorder evidence, Clock clock) {
        this.composer = composer;
        this.evidence = evidence;
        this.clock = clock;
    }

    /** A quote: a plan of one step, convert. */
    public Run quote(String requestId, BigDecimal amount, String from, String to) {
        return run(requestId, QUOTE, new Aspect(QUOTE_REQUEST, Envelope.body()
                .put("amount", amount.toPlainString()).put("from", from).put("to", to)));
    }

    /** An account's value: load its positions, convert each to {@code currency}, add them up. */
    public Run accountValue(String requestId, String account, String currency) {
        return run(requestId, ACCOUNT_VALUE, new Aspect(ACCOUNT_VALUE_REQUEST, Envelope.body()
                .put("account", account).put("currency", currency)));
    }

    private Run run(String requestId, Address outcome, Aspect input) {
        Run run = composer.run(outcome, Envelope.request(input, requestId));
        if (!run.failed()) {
            run.completed().whenComplete((done, e) -> {
                if (e != null) {
                    LOG.warn("couldn't finish recording {} (request {})", outcome, requestId, e);
                } else {
                    record(requestId, run, done);
                }
            });
        }
        return run;
    }

    /** Every pricing call, compared or not, and, if anything was compared, the outcome's shadow result. */
    private void record(String requestId, Run run, Run.Completed done) {
        String topology = done.topology() == null ? null : done.topology().toString();
        for (Run.StepRun s : run.steps()) {
            if (!ReloadableRouting.CAPABILITY.equals(s.step().capability().name())) {
                continue;
            }
            for (Run.Call c : s.calls()) {
                evidence.record(pricingCall(requestId, run.generation(), s, c, topology));
            }
        }
        if (done.compared()) {
            evidence.outcome(new EvidenceRecorder.OutcomeCall(run.generation(), clock.instant(), requestId,
                    run.recipe().address().toString(), run.result(), done.shadowResult(), done.shadowError(), topology));
        }
    }

    private EvidenceRecorder.Call pricingCall(String requestId, long generation, Run.StepRun s, Run.Call c,
            String topology) {
        JsonNode in = c.request().payload().body();
        var served = s.route().served();
        var other = s.route().compared();
        Run.Compared compared = c.compared() == null ? null : c.compared().join();
        BigDecimal shadowValue = null;
        String shadowError = null;
        if (compared != null) {
            if (compared.ok()) {
                shadowValue = amount(compared.reply());
            } else {
                shadowError = compared.error();
            }
        }
        return new EvidenceRecorder.Call(generation, clock.instant(), requestId, new BigDecimal(in.path("amount").asText()),
                in.path("from").asText(), in.path("to").asText(), served.resolved().toString(), served.binding(),
                served.role(), amount(c.reply()), other == null ? null : other.resolved().toString(), shadowValue,
                shadowError, EvidenceRecorder.Timings.of(c.reply().timing()),
                compared == null || compared.reply() == null ? null : EvidenceRecorder.Timings.of(compared.reply().timing()),
                topology, identity(c.answered()), compared == null ? null : identity(compared.answered()));
    }

    private static BigDecimal amount(Reply reply) {
        return new BigDecimal(reply.envelope().payload().body().path("amount").asText());
    }

    private static String identity(InstanceRef i) {
        return i == null ? null : i.identity();
    }
}
