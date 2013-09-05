/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock AS. All Rights Reserved
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

import java.util.ArrayList;
import java.util.List;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.ldap.search.LdapInternalSearch;

/**
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */

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
        catch(NamingException e){}
        
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
}
