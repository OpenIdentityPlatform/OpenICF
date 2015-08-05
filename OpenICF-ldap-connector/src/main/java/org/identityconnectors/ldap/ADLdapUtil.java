/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2015 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.ldap;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.ldap.LdapConstants.ServerType;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import static org.identityconnectors.ldap.LdapUtil.escapeDNValueOfJNDIReservedChars;

/* 
 * This class provides static helper methods to handle 
 * some MS AD specific attributes over LDAP
 */
public class ADLdapUtil {
    
    private static final Log log = Log.getLog(ADLdapUtil.class);
    
    /* 
     * Maximum number of members retrieved from a group in one search 
     */
    public static final int GROUP_MEMBERS_MAXRANGE = 1500;
    
    /*
    * The time difference between Java and .Net for Dates
    */
    public static final long DIFF_NET_JAVA_FOR_DATE_AND_TIMES = 11644473600000L;
    
    static String AddLeadingZero(int k) {
            return (k<=0xF)?"0" + Integer.toHexString(k):Integer.toHexString(k);
    }

    public static String objectGUIDtoDashedString(Attribute attr){
        byte[] GUID = null;
        try{
            GUID = (byte[])attr.get();
            if (GUID.length != 16){
                throw new ConnectorException(LdapConstants.MS_GUID_ATTR+" attribute has the wrong length ("+GUID.length+"). Should be 16 bytes.");
            }
        }
        catch(NamingException e){}
        
        StringBuilder sGUID = new StringBuilder(43);
        sGUID.append("<GUID=");
        sGUID.append(AddLeadingZero((int)GUID[3] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[2] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[1] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[0] & 0xFF));
        sGUID.append("-");
        sGUID.append(AddLeadingZero((int)GUID[5] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[4] & 0xFF));
        sGUID.append("-");
        sGUID.append(AddLeadingZero((int)GUID[7] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[6] & 0xFF));
        sGUID.append("-");
        sGUID.append(AddLeadingZero((int)GUID[8] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[9] & 0xFF));
        sGUID.append("-");
        sGUID.append(AddLeadingZero((int)GUID[10] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[11] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[12] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[13] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[14] & 0xFF));
        sGUID.append(AddLeadingZero((int)GUID[15] & 0xFF));
        sGUID.append(">");
        
        return sGUID.toString();
    }
    
    public static String objectGUIDtoString(Attribute attr){
        byte[] GUID = null;
        try{
            GUID = (byte[])attr.get();
            if (GUID.length != 16){
                throw new ConnectorException(LdapConstants.MS_GUID_ATTR+" attribute has the wrong length ("+GUID.length+"). Should be 16 bytes.");
            }
        }
        catch(NamingException e){
            log.error(e, "Error reading " + attr.getID() + " attribute");
        }
        
        StringBuilder sGUID = new StringBuilder(39);
        sGUID.append("<GUID=");
        for(int i=0;i<16;i++){
            sGUID.append(AddLeadingZero((int)GUID[i] & 0xFF));
        }
        sGUID.append(">");
        return sGUID.toString();
    }
    
    public static String guidDashedStringtoByteString(String dashed){
        // <GUID=ac642e6e-6ab5-425a-bcc9-9f5067d46e3f>
        //   [3][2][1][0]-[5][4]-[7][6]-[8][9]-[10][11][12][13][14][15]
        // We reorder the bytes...
        if (dashed.length() != 43){
            throw new ConnectorException(LdapConstants.MS_GUID_ATTR+" attribute has the wrong length ("+dashed.length()+"). Should be 43 characters.");
        }
        StringBuilder bString = new StringBuilder("\\");
        bString.append(dashed.substring(12,14));
        bString.append("\\");
        bString.append(dashed.substring(10, 12));
        bString.append("\\");
        bString.append(dashed.substring(8, 10));
        bString.append("\\");
        bString.append(dashed.substring(6, 8));
        bString.append("\\");
        bString.append(dashed.substring(17, 19));
        bString.append("\\");
        bString.append(dashed.substring(15, 17));
        bString.append("\\");
        bString.append(dashed.substring(22, 24));
        bString.append("\\");
        bString.append(dashed.substring(20, 22));
        bString.append("\\");
        bString.append(dashed.substring(25, 27));
        bString.append("\\");
        bString.append(dashed.substring(27, 29));
        bString.append("\\");
        bString.append(dashed.substring(30, 32));
        bString.append("\\");
        bString.append(dashed.substring(32, 34));
        bString.append("\\");
        bString.append(dashed.substring(34, 36));
        bString.append("\\");
        bString.append(dashed.substring(36, 38));
        bString.append("\\");
        bString.append(dashed.substring(38, 40));
        bString.append("\\");
        bString.append(dashed.substring(40, 42));
        
        return bString.toString();
    }
    
    public static String guidStringtoByteString(String dashed){
        // <GUID=2c6bfee3175c0a4e9af01182a2fb0ae1>
        if (dashed.length() != 39){
            throw new ConnectorException(LdapConstants.MS_GUID_ATTR+" attribute has the wrong length ("+dashed.length()+"). Should be 39 characters.");
        }
        StringBuilder bString = new StringBuilder();
        for (int i=6;i<37;i=i+2){
            bString.append("\\");
            bString.append(dashed.substring(i,i+2));
        }
        return bString.toString();
    }
    
    public static String objectSIDtoString(Attribute attr) {
        byte[] SID = null;
        try{
            SID = (byte[])attr.get();
        }
        catch(NamingException e){
            log.error(e, "Error reading " + attr.getID() + " attribute");
        }
        return objectSIDtoString(SID);
    }

    public static String objectSIDtoString(byte[] SID) {
        if (SID == null) {
            return null;
        }
        
        if (SID.length < 8 || SID.length > 68){
            throw new ConnectorException(LdapConstants.MS_SID_ATTR+" attribute has the wrong length ("+SID.length+"). Should be between 8 and 68 bytes.");
        }
        
        // Add the 'S' prefix
        StringBuilder strSID = new StringBuilder("S-");

        // bytes[0] : in the array is the version (must be 1 but might 
        // change in the future)
        strSID.append(SID[0]).append('-');

        // bytes[2..7] : the Authority
        StringBuilder tmpBuff = new StringBuilder();
        for (int t = 2; t <= 7; t++) {
            tmpBuff.append(AddLeadingZero((int) SID[t] & 0xFF));
        }
        strSID.append(Long.parseLong(tmpBuff.toString(), 16));

        // bytes[1] : the sub authorities count
        int count = SID[1];

        // bytes[8..end] : the sub authorities (these are Integers - notice
        // the endian)
        for (int i = 0; i < count; i++) {
            int currSubAuthOffset = i * 4;
            tmpBuff.setLength(0);
            tmpBuff.append(String.format("%02X%02X%02X%02X",
                    (SID[11 + currSubAuthOffset] & 0xFF),
                    (SID[10 + currSubAuthOffset] & 0xFF),
                    (SID[9 + currSubAuthOffset] & 0xFF),
                    (SID[8 + currSubAuthOffset] & 0xFF)));

            strSID.append('-').append(Long.parseLong(tmpBuff.toString(), 16));
        }

        // That's it - we have the SID
        return strSID.toString();
    }
    
    public static List fetchTokenGroupsByDn(LdapConnection conn, LdapEntry entry) {
        List groups = new ArrayList();
        try {
            Attributes attrs = conn.getInitialContext().getAttributes(escapeDNValueOfJNDIReservedChars(entry.getDN().toString()), new String[]{LdapConstants.MS_TOKEN_GROUPS_ATTR});
            Attribute attr = attrs.get(LdapConstants.MS_TOKEN_GROUPS_ATTR);
            NamingEnumeration ae = attr.getAll();
            while (ae.hasMore()) {
                groups.add(objectSIDtoString((byte[])ae.next()));
            }
        } catch (NamingException e) {
            log.error(e, "Error reading tokenGroups attribute");
        }
        return groups;
    }
    
    public static List fetchGroupMembersByRange(LdapConnection conn, SearchResult result){
        return fetchGroupMembersByRange(conn, LdapEntry.create(null, result));
    }
    
    /*
     * This method returns the list of members when the group has over 1500 members.
     * 
     */
    public static List fetchGroupMembersByRange(LdapConnection conn, LdapEntry entry){
        boolean done = false;
        int first = 0;
        int last = GROUP_MEMBERS_MAXRANGE -1;
        List members = new ArrayList();
        // get the first slice (0-1499)
        org.identityconnectors.framework.common.objects.Attribute range = conn.getSchemaMapping().createAttribute(ObjectClass.GROUP, String.format("member;range=%d-%d",first,last), entry, false);
        if (range != null){
            members.addAll(range.getValue());
            first = last +1;
            last = first + GROUP_MEMBERS_MAXRANGE -1;
            
            while (!done) {
                try {
                    SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
                    controls.setSearchScope(SearchControls.OBJECT_SCOPE);
                    controls.setReturningAttributes(new String[]{String.format("member;range=%d-%d", first, last)});
                    LdapContext context = conn.getInitialContext().newInstance(null);
                    NamingEnumeration<SearchResult> entries = context.search(entry.getDN(), "objectclass=*", controls);
                    SearchResult res = entries.next();
                    if (res != null) {
                        range = conn.getSchemaMapping().createAttribute(ObjectClass.GROUP, String.format("member;range=%d-%d", first, last), LdapEntry.create(null,res), true);
                        // we hit the last slice... so the range ends with an '*'
                        if (range.getValue().isEmpty()) {
                            range = conn.getSchemaMapping().createAttribute(ObjectClass.GROUP, String.format("member;range=%d-*", first), LdapEntry.create(null,res), true);
                            done = true;
                        }
                        members.addAll(range.getValue());
                    }
                } catch (NamingException e) {
                    log.error(e, "Error reading group member;range attribute");
                }
                first = last + 1;
                last = first + GROUP_MEMBERS_MAXRANGE -1;
            }
        }
        return members;
    }
    
    public static Date getJavaDateFromADTime(String adTime) {
        long milliseconds = (Long.parseLong(adTime) / 10000) - DIFF_NET_JAVA_FOR_DATE_AND_TIMES;
        return new Date(milliseconds);
    }
    
    public static String getADTimeFromJavaDate(Date date) {
        return Long.toString((date.getTime()  + DIFF_NET_JAVA_FOR_DATE_AND_TIMES)* 10000);
    }
    
    public static String getADTimeFromISO8601Date(String date) throws ParseException{
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return getADTimeFromJavaDate(df.parse(date));
    }
    
    public static String getADLdapDatefromJavaDate(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date)+".0Z";
    }
    
    public static String getISO8601DatefromJavaDate(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }
    
    
    public static org.identityconnectors.framework.common.objects.Attribute convertMSEpochToISO8601(Attribute attr){
        if (attr != null){
            String attrName = attr.getID();
            try {
                String value = (String) attr.get();
                if (ADUserAccountControl.ACCOUNT_EXPIRES.equalsIgnoreCase(attrName) && ADUserAccountControl.ACCOUNT_NEVER_EXPIRES.equalsIgnoreCase(value)) {
                    value = "0";
                }
                if ("0".equalsIgnoreCase(value)) {
                    return AttributeBuilder.build(attrName, "0");
                } else {
                    Date date = getJavaDateFromADTime(value);
                    return AttributeBuilder.build(attrName, getISO8601DatefromJavaDate(date));
                }
            } catch (NamingException ex) {
                log.warn("Attribute {0} can not be read from entry", attrName);
            }
        }
        return null;
    }
    
    public static boolean isServerMSADFamily(ServerType type){
        switch (type) {
            case MSAD:
            case MSAD_GC:
            case MSAD_LDS:
                return true;
            default:
                return false;
        }
    }
}