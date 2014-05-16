/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.as.rest.v2.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.intel.mtwilson.jaxrs2.DocumentCollection;
import java.util.ArrayList;
import java.util.List;
//import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 *
 * @author ssbangal
 */
public class HostAikCertificateCollection extends DocumentCollection<HostAikCertificate> {
    
    private final ArrayList<HostAikCertificate> aikCerts = new ArrayList<HostAikCertificate>();
    
//    @JsonSerialize(include=JsonSerialize.Inclusion.ALWAYS) // jackson 1.9
    @JsonInclude(JsonInclude.Include.ALWAYS)                // jackson 2.0
    @JacksonXmlElementWrapper(localName="host_aik_certificates")
    @JacksonXmlProperty(localName="host_aik_certificate")    
    public List<HostAikCertificate> getAikCertificates() { return aikCerts; }
    
    @Override
    public List<HostAikCertificate> getDocuments() {
        return getAikCertificates();
    }
    
    
}
