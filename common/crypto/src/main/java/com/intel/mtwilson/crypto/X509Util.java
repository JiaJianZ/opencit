/*
 * Copyright (C) 2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.x509.GeneralNameInterface;

/**
 *
 * @author jbuhacoff
 */
public class X509Util {
    private static Logger log = LoggerFactory.getLogger(X509Util.class);
    public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
    public static final String PEM_NEWLINE = "\r\n";

    /**
     * See also RsaCredential in the security project.
     * See also Sha256Digest in the datatypes project
     * @param certificate
     * @return
     */
    public static byte[] sha256fingerprint(X509Certificate certificate) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        byte[] digest = hash.digest(certificate.getEncoded());
        return digest;
    }

    /**
     * Provided for compatibility with other systems.
     * See also Sha1Digest in the datatypes project
     * @param certificate
     * @return
     */
    public static byte[] sha1fingerprint(X509Certificate certificate) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest hash = MessageDigest.getInstance("SHA-1");
        byte[] digest = hash.digest(certificate.getEncoded());
        return digest;
    }
    
    /**
     * Converts an X509 Certificate to PEM encoding, with lines up to 76 characters long.
     * Newlines are carriage-return and line-feed. 
     * The end certificate tag also ends in a newline, so you can output a sequence of
     * pem certificates into a file without having to insert any newlines yourself.
     * @param certificate
     * @return
     * @throws CertificateEncodingException 
     */
    public static String encodePemCertificate(X509Certificate certificate) throws CertificateEncodingException {
        // the function Base64.encodeBase64String does not chunk to 76 characters per line
        String encoded = new String(Base64.encodeBase64(certificate.getEncoded(), true));
        return BEGIN_CERTIFICATE+PEM_NEWLINE+encoded.trim()+PEM_NEWLINE+END_CERTIFICATE+PEM_NEWLINE;
    }
    
    /**
     * This function converts a PEM-format certificate to an X509Certificate
     * object.
     *
     * Example PEM format:
     *
     * -----BEGIN CERTIFICATE----- (base64 data here) -----END CERTIFICATE-----
     *
     * You can also pass just the base64 certificate data without the header and
     * footer.
     * 
     * @param text
     * @return
     * @throws CertificateException 
     */
    public static X509Certificate decodePemCertificate(String text) throws CertificateException {
        String content = text.replace(BEGIN_CERTIFICATE, "").replace(END_CERTIFICATE, "");
        byte[] der = Base64.decodeBase64(content);
        return decodeDerCertificate(der);
    }
    
    public static List<X509Certificate> decodePemCertificates(String text) throws CertificateException {
        String[] pems = StringUtils.splitByWholeSeparator(text, END_CERTIFICATE);
        ArrayList<X509Certificate> certs = new ArrayList<X509Certificate>(pems.length);
        for(String pem : pems) {
            if( pem.trim().isEmpty() ) { continue; }
            certs.add(decodePemCertificate(pem));
        }
        return certs;
    }

    /**
     * Reads a DER-encoded certificate and creates a corresponding X509Certificate
     * object.
     * @param certificateBytes
     * @return
     * @throws CertificateException 
     */
    public static X509Certificate decodeDerCertificate(byte[] certificateBytes) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateBytes));
        return cert;
    }
    
    /**
     * For completeness.  The X509Certificate.getEncoded() method returns the DER
     * encoding of the certificate.
     * @param certificate
     * @return
     * @throws CertificateEncodingException 
     */
    public static byte[] encodeDerCertificate(X509Certificate certificate) throws CertificateEncodingException {
        return certificate.getEncoded();
    }

    /**
     * If the X509 certificate has a Subject Alternative Name which is an IP
     * Address, then it will be returned as a String. If there is more than one,
     * only the first is returned. If there are none, null will be
     * returned.
     *
     * The X509Certificate method getSubjectAlternativeNames() returns a list of
     * (type,value) pairs. The types are shown in this extract of the JavaDoc:
     *     *
    GeneralName ::= CHOICE { otherName [0] OtherName, rfc822Name [1]
     * IA5String, dNSName [2] IA5String, x400Address [3] ORAddress,
     * directoryName [4] Name, ediPartyName [5] EDIPartyName,
     * uniformResourceIdentifier [6] IA5String, iPAddress [7] OCTET STRING,
     * registeredID [8] OBJECT IDENTIFIER}
     *
     *
     * The IP Address is type 7
     *
     * @param certificate
     * @return
     */
    public static String ipAddressAlternativeName(X509Certificate certificate) {
        try {
            Collection<List<?>> wtf = certificate.getSubjectAlternativeNames();
            if (wtf == null) {
                return null;
            } // when certificate does not have the alternative names extension at all
            Iterator<List<?>> it1 = wtf.iterator();
            while (it1.hasNext()) {
                List<?> list = it1.next();
                if (list.size() == 2 && list.get(0) != null && list.get(0) instanceof Integer) {
                    Integer type = (Integer) list.get(0);
                    if (type == GeneralNameInterface.NAME_IP && list.get(1) != null && list.get(1) instanceof String) {
                        String ipAddress = (String) list.get(1);
                        return ipAddress;
                    }
                }
            }
        } catch (CertificateParsingException e) {
            log.error("Cannot extract Subject Alternative Name IP Address from X509 Certificate", e);
        }
        return null;
    }

    /**
     * Currently only retrieves IP Address and DNS alternative names.
     * @param certificate
     * @return set of alternative names, or empty set if none are found
     */
    public static Set<String> alternativeNames(X509Certificate certificate) {
        try {
            Collection<List<?>> wtf = certificate.getSubjectAlternativeNames();
            if (wtf == null) { // when certificate does not have the alternative names extension at all
                return Collections.EMPTY_SET;
            }
            HashSet<String> ipNames = new HashSet<String>();
            Iterator<List<?>> it1 = wtf.iterator();
            while (it1.hasNext()) {
                List<?> list = it1.next();
                if (list.size() == 2 && list.get(0) != null && list.get(0) instanceof Integer) {
                    Integer type = (Integer) list.get(0);
                    if (type == GeneralNameInterface.NAME_IP && list.get(1) != null && list.get(1) instanceof String) {
                        String ipAddress = (String) list.get(1);
                        ipNames.add(ipAddress);
                    }
                    if (type == GeneralNameInterface.NAME_DNS && list.get(1) != null && list.get(1) instanceof String) {
                        String dnsAddress = (String) list.get(1);
                        ipNames.add(dnsAddress);
                    }
                }
            }
            return ipNames;
        } catch (CertificateParsingException e) {
            log.error("Cannot extract Subject Alternative Names from X509 Certificate", e);
            return Collections.EMPTY_SET;
        }
    }
    
    public static boolean isCA(X509Certificate certificate) {
        return certificate.getBasicConstraints() > -1;  // -1 indicates not a CA cert, 0 and above indicates CA cert
    }

}
