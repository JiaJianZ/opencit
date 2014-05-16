/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.intel.mtwilson.tag.rest.v2.resource;

import com.intel.dcsg.cpg.io.UUID;
import com.intel.mtwilson.My;
import com.intel.mtwilson.MyFilesystem;
import com.intel.mtwilson.tag.model.x509.UTF8NameValueSequence;
import com.intel.mtwilson.tag.model.Selection;
import com.intel.mtwilson.tag.model.SelectionCollection;
import com.intel.mtwilson.tag.model.SelectionFilterCriteria;
import com.intel.mtwilson.tag.model.SelectionLocator;
import com.intel.mtwilson.jaxrs2.NoLinks;
import com.intel.mtwilson.jaxrs2.mediatype.CryptoMediaType;
import com.intel.mtwilson.jaxrs2.mediatype.DataMediaType;
import com.intel.mtwilson.jaxrs2.server.resource.AbstractJsonapiResource;
import com.intel.mtwilson.launcher.ws.ext.V2;
import com.intel.mtwilson.tag.TagConfiguration;
import com.intel.mtwilson.tag.Util;
import com.intel.mtwilson.tag.dao.TagJdbi;
import com.intel.mtwilson.tag.dao.jdbi.SelectionKvAttributeDAO;
import com.intel.mtwilson.tag.model.SelectionKvAttribute;
import com.intel.mtwilson.tag.rest.v2.repository.SelectionRepository;
import com.intel.mtwilson.tag.selection.SelectionBuilder;
import com.intel.mtwilson.tag.selection.xml.SelectionsType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
//import org.restlet.data.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author ssbangal
 */
@V2
@Path("/tag-selections")
public class Selections extends AbstractJsonapiResource<Selection, SelectionCollection, SelectionFilterCriteria, NoLinks<Selection>, SelectionLocator> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Selections.class);

    private SelectionRepository repository;
    
    public Selections() {
        repository = new SelectionRepository();
    }
    
    @Override
    protected SelectionCollection createEmptyCollection() {
        return new SelectionCollection();
    }

    @Override
    protected SelectionRepository getRepository() {
        return repository;
    }

    @Override
    @Path("/{id}")
    @GET
    @RequiresPermissions("tag_selections:retrieve")         
    @Produces({DataMediaType.APPLICATION_YAML, DataMediaType.TEXT_YAML})   
    public Selection retrieveOne(@BeanParam SelectionLocator locator) {
        return super.retrieveOne(locator); 
    }
        
    
    @Path("/{id}")
    @GET
    @Produces({MediaType.APPLICATION_JSON})   
    @RequiresPermissions("tag_selections:retrieve")         
    public String retrieveOneJson(@BeanParam SelectionLocator locator) throws SQLException, IOException {
        Selection obj = super.retrieveOne(locator); 
        SelectionsType selectionsType = getSelectionData(obj);
        String jsonStr = Util.toJson(selectionsType);
        log.debug("Generated tag selection json: {}", jsonStr);
        return jsonStr;
        
    }
            
    @Path("/{id}")
    @GET
    @Produces({MediaType.APPLICATION_XML})   
    @RequiresPermissions("tag_selections:retrieve")         
    public String retrieveOneXml(@BeanParam SelectionLocator locator) throws SQLException, IOException {
        Selection obj = super.retrieveOne(locator); //To change body of generated methods, choose Tools | Templates.
//        if( obj == null ) {
//            return null;
//        }
//        List<SelectionKvAttribute> selectionKvAttributes = attrDao.findBySelectionIdWithValues(obj.getId());
//        if( selectionKvAttributes == null || selectionKvAttributes.isEmpty() ) {
//            log.error("No tags in selection");
//            return null;
//        }
//        SelectionBuilder builder = SelectionBuilder.factory().selection();
//        for (SelectionKvAttribute kvAttribute : selectionKvAttributes) {
//            builder.textAttributeKV(kvAttribute.getKvAttributeName(), kvAttribute.getKvAttributeValue());
//        } 
//        // TODO:  if there are any other attributes such as 2.5.4.789.2 or custom ones they should be added here too
//        SelectionsType selectionsType = builder.build();
        SelectionsType selectionsType = getSelectionData(obj);
        String xml = Util.toXml(selectionsType);
        log.debug("Generated tag selection xml: {}", xml);
        return xml;
    }
    
    private SelectionsType getSelectionData(Selection selectionObj) throws SQLException{
        try(SelectionKvAttributeDAO attrDao = TagJdbi.selectionKvAttributeDao()) {

            if( selectionObj == null ) {
                return null;
            }
            List<SelectionKvAttribute> selectionKvAttributes = attrDao.findBySelectionIdWithValues(selectionObj.getId());
            if( selectionKvAttributes == null || selectionKvAttributes.isEmpty() ) {
                log.error("No tags in selection");
                return null;
            }
            SelectionBuilder builder = SelectionBuilder.factory().selection();
            for (SelectionKvAttribute kvAttribute : selectionKvAttributes) {
                builder.textAttributeKV(kvAttribute.getKvAttributeName(), kvAttribute.getKvAttributeValue());
            } 
            // TODO:  if there are any other attributes such as 2.5.4.789.2 or custom ones they should be added here too
            SelectionsType selectionsType = builder.build();

            return selectionsType;
        }
    }

    @GET
    @Path("/{id}")
    @Produces(CryptoMediaType.MESSAGE_RFC822)   
    @RequiresPermissions("tag_selections:retrieve")         
    public String retrieveOneEncryptedXml(@BeanParam SelectionLocator locator) throws SQLException, IOException {
        String xml = retrieveOneXml(locator);
        TagConfiguration configuration = new TagConfiguration(My.configuration().getConfiguration());
        UUID uuid = new UUID();
        String plaintextFilePath = MyFilesystem.getApplicationFilesystem().getFeatureFilesystem("tag").getVarPath() + File.separator + uuid.toString() + ".xml";
        String encryptedFilePath = MyFilesystem.getApplicationFilesystem().getFeatureFilesystem("tag").getVarPath() + File.separator + uuid.toString() + ".enc";
        File plaintextFile = new File(plaintextFilePath);
        try(FileOutputStream out = new FileOutputStream(plaintextFile)) {
            IOUtils.write(xml, out);
        }
        String tagCmdPath = MyFilesystem.getApplicationFilesystem().getFeatureFilesystem("tag").getBinPath();
        log.debug("Tag command path: {}", tagCmdPath);
        Process process = Runtime.getRuntime().exec(tagCmdPath+File.separator+"encrypt.sh -p PASSWORD --nopbkdf2 "+ encryptedFilePath+" "+plaintextFilePath, new String[] { "PASSWORD="+configuration.getTagProvisionXmlEncryptionPassword() });
        try { 
            int exitValue = process.waitFor();
            if( exitValue != 0 ) { // same as exitValue but waits for process to end first; prevents java.lang.IllegalThreadStateException: process hasn't exited        at java.lang.UNIXProcess.exitValue(UNIXProcess.java:217)
                throw new IOException("Failed to encrypt file (error "+exitValue+")");
            }
        }
        catch(InterruptedException e) {
                throw new IOException("Failed to encrypt file (interrupted)", e);
        }
        File encryptedFile = new File(encryptedFilePath);
        try(FileInputStream in = new FileInputStream(encryptedFile)) {
            String encryptedXml = IOUtils.toString(in);
            return encryptedXml;
        }
    }
    
}
