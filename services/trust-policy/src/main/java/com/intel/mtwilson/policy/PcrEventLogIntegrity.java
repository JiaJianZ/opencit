/*
 * Copyright (C) 2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.policy;

import com.intel.mtwilson.model.Measurement;
import com.intel.mtwilson.model.Pcr;
import com.intel.mtwilson.model.PcrEventLog;
import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.Sha1Digest;
import com.intel.mtwilson.policy.fault.PcrManifestMissing;
import com.intel.mtwilson.policy.fault.PcrValueMismatch;
import com.intel.mtwilson.policy.fault.PcrValueMissing;
import java.util.List;

/**
 * The PcrMatchesConstant policy enforces that a specific PCR contains a specific 
 * pre-determined constant value. This is typical for values that are known in 
 * advance such as BIOS or trusted module measurements.
 * 
 * The PcrEventLogIncludes and PcrEventLogEquals policies enforce that the event log
 * for a specific PCR contain certain measurements.
 * 
 * This policy, PcrEventLogIntegrity, is a complement to the other PcrEventLog* policies
 * because it checks that the PCR value is equal to the result of extending all the
 * measurements in the event log.  If this policy is applied to a host and it fails,
 * then results from the other PcrEventLog* may not be trustworthy since the event log
 * integrity cannot be verified -- that is it can contain any list of modules and we
 * don't know if it's accurate (and must assume it isn't).
 * 
 * 
 * @author jbuhacoff
 */
public class PcrEventLogIntegrity implements TrustPolicy {
    private PcrIndex pcrIndex;
    public PcrEventLogIntegrity(PcrIndex pcrIndex) {
        this.pcrIndex = pcrIndex;
    }
    
    public PcrIndex getPcrIndex() { return pcrIndex; }
    
    @Override
    public TrustReport apply(HostReport hostReport) {
        TrustReport report = new TrustReport(this);
        if( hostReport.pcrManifest == null ) {
            report.fault(new PcrManifestMissing());            
        }
        else {
            Pcr actualValue = hostReport.pcrManifest.getPcr(pcrIndex);
            if( actualValue == null ) {
                report.fault(new PcrValueMissing(pcrIndex));
            }
            else {
                PcrEventLog eventLog = hostReport.pcrManifest.getPcrEventLog(pcrIndex);
                Sha1Digest expectedValue = computeHistory(eventLog.getEventLog()); // calculate expected' based on history
                
                // make sure the expected pcr value matches the actual pcr value
                if( !expectedValue.equals(actualValue.getValue()) ) {
                    report.fault(new PcrValueMismatch(pcrIndex, expectedValue, actualValue.getValue()) );
                }
            }
        }
        return report;
    }
    
    private Sha1Digest computeHistory(List<Measurement> list) {
        // start with a default value of zero...  that should be the initial value of every PCR ..  if a pcr is reset after boot the tpm usually sets its starting value at -1 so the end result is different , which we could then catch here when the hashes don't match
        Sha1Digest result = Sha1Digest.ZERO;
        for(Measurement m : list) {
            result = result.extend(m.getValue());
        }
        return result;
    }
}
