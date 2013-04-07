/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.policy.impl;

import com.intel.mtwilson.policy.RequireAll;
import com.intel.mtwilson.policy.TrustPolicy;
import java.util.List;

/**
 *
 * @author jbuhacoff
 */
public class TrustedLocation extends RequireAll {
    public TrustedLocation(List<TrustPolicy> required) {
        super(required);
    }
    
}
