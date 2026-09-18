package org.openelisglobal.analyzerimport.samplenumber;

import java.sql.Timestamp;
import java.util.List;
import org.openelisglobal.sample.valueholder.Sample;

public interface AnalyzerSampleAmbiguityService {

    void parkIncoming(String incomingIdentifier, String analyzerId, String analyzerTestCode, String testId,
            String testName, String resultValue, String resultUnits, String resultType, Timestamp completeDate,
            List<Sample> candidates);

    List<AnalyzerSampleAmbiguityCard> listPending();

    List<AnalyzerSampleAmbiguityCard> listPending(int offset, int limit);

    long countPending();

    boolean promoteToHolding(Integer pendingId, String sampleId, String sysUserId);

    boolean discard(Integer pendingId, String sysUserId);
}
