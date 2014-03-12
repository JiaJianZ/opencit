/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.tag.rest.v2.resource;

import com.intel.mtwilson.tag.model.HostUuid;
import com.intel.mtwilson.tag.model.HostUuidCollection;
import com.intel.mtwilson.tag.model.HostUuidFilterCriteria;
import com.intel.mtwilson.tag.model.HostUuidLocator;
import com.intel.mtwilson.jersey.NoLinks;
import com.intel.mtwilson.jersey.resource.AbstractJsonapiResource;
import com.intel.mtwilson.launcher.ws.ext.V2;
import com.intel.mtwilson.tag.rest.v2.repository.HostUuidRepository;
import javax.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ssbangal
 */
@V2
@Path("/host_uuids")
public class HostUuids extends AbstractJsonapiResource<HostUuid, HostUuidCollection, HostUuidFilterCriteria, NoLinks<HostUuid>, HostUuidLocator> {

    private Logger log = LoggerFactory.getLogger(getClass().getName());
    private HostUuidRepository repository;
    
    public HostUuids() {
        repository = new HostUuidRepository();
    }
    
    @Override
    protected HostUuidCollection createEmptyCollection() {
        return new HostUuidCollection();
    }

    @Override
    protected HostUuidRepository getRepository() {
        return repository;
    }
        
}
