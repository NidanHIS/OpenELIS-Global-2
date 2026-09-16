package org.openelisglobal.analyzerimport.samplenumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.openelisglobal.sample.valueholder.Sample;

public final class AnalyzerSampleResolveOutcome {

    public enum Kind {
        NONE, UNIQUE, AMBIGUOUS
    }

    private final Kind kind;
    private final Sample sample;
    private final List<Sample> candidates;

    private AnalyzerSampleResolveOutcome(Kind kind, Sample sample, List<Sample> candidates) {
        this.kind = kind;
        this.sample = sample;
        this.candidates = candidates != null ? candidates : Collections.emptyList();
    }

    public static AnalyzerSampleResolveOutcome none() {
        return new AnalyzerSampleResolveOutcome(Kind.NONE, null, Collections.emptyList());
    }

    public static AnalyzerSampleResolveOutcome unique(Sample sample) {
        if (sample == null) {
            return none();
        }
        return new AnalyzerSampleResolveOutcome(Kind.UNIQUE, sample, Collections.singletonList(sample));
    }

    public static AnalyzerSampleResolveOutcome ambiguous(List<Sample> candidates) {
        if (candidates == null || candidates.size() < 2) {
            if (candidates != null && candidates.size() == 1) {
                return unique(candidates.get(0));
            }
            return none();
        }
        return new AnalyzerSampleResolveOutcome(Kind.AMBIGUOUS, null, new ArrayList<>(candidates));
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    public boolean isUnique() {
        return kind == Kind.UNIQUE;
    }

    public boolean isAmbiguous() {
        return kind == Kind.AMBIGUOUS;
    }

    public Sample getSample() {
        return sample;
    }

    public List<Sample> getCandidates() {
        return candidates;
    }
}
