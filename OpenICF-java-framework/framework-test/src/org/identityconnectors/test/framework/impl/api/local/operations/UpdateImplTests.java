/*
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * U.S. Government Rights - Commercial software. Government users 
 * are subject to the Sun Microsystems, Inc. standard license agreement
 * and applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * Sun, Sun Microsystems, the Sun logo, Java and Project Identity 
 * Connectors are trademarks or registered trademarks of Sun 
 * Microsystems, Inc. or its subsidiaries in the U.S. and other
 * countries.
 * 
 * UNIX is a registered trademark in the U.S. and other countries,
 * exclusively licensed through X/Open Company, Ltd. 
 * 
 * -----------
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License(CDDL) (the License).  You may not use this file
 * except in  compliance with the License. 
 * 
 * You can obtain a copy of the License at
 * http://identityconnectors.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing permissions and 
 * limitations under the License.  
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * -----------
 */
package org.identityconnectors.test.framework.impl.api.local.operations;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.api.operations.UpdateApiOp.Type;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.impl.api.local.operations.UpdateImpl;
import org.junit.Assert;
import org.junit.Test;


/**
 * Testing for the merging of the various attribute during update.
 */
public class UpdateImplTests {

    @Test(expected=NullPointerException.class)
    public void validateTypeArg() {
        UpdateImpl.validateInput(null, ObjectClass.ACCOUNT, new HashSet<Attribute>());
    }

    @Test(expected=NullPointerException.class)
    public void validateObjectClassArg() {
        UpdateImpl.validateInput(Type.ADD, null, new HashSet<Attribute>());
    }
    
    @Test(expected=NullPointerException.class)
    public void validateAttrsArg() {
        UpdateImpl.validateInput(Type.ADD, ObjectClass.ACCOUNT, null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void validateNoUidAttribute() {
        UpdateImpl.validateInput(Type.ADD, ObjectClass.ACCOUNT, new HashSet<Attribute>());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void validateAddWithNullAttribute() {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(AttributeBuilder.build("something"));
        UpdateImpl.validateInput(Type.ADD, ObjectClass.ACCOUNT, attrs);        
    }

    @Test(expected=IllegalArgumentException.class)
    public void validateDeleteWithNullAttribute() {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(AttributeBuilder.build("something"));
        UpdateImpl.validateInput(Type.DELETE, ObjectClass.ACCOUNT, attrs);        
    }

    @Test(expected=IllegalArgumentException.class)
    public void validateAttemptToAddName() {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(new Name("fadf"));
        attrs.add(new Uid("1"));
        UpdateImpl.validateInput(Type.ADD, ObjectClass.ACCOUNT, attrs);                
    }

    @Test(expected=IllegalArgumentException.class)
    public void validateAttemptToDeleteName() {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(new Name("fadf"));
        attrs.add(new Uid("1"));
        UpdateImpl.validateInput(Type.DELETE, ObjectClass.ACCOUNT, attrs);                
    }

    @Test
    public void validateAttemptToAddDeleteOperationalAttribute() {
        // list of all the operational attributes..
        List<Attribute> list = new ArrayList<Attribute>();
        list.add(AttributeBuilder.buildEnabled(false));
        list.add(AttributeBuilder.buildLockOut(true));
        list.add(AttributeBuilder.buildCurrentPassword("fadsf".toCharArray()));
        list.add(AttributeBuilder.buildPasswordExpirationDate(new Date()));
        list.add(AttributeBuilder.buildPassword("fadsf".toCharArray()));
        for (Attribute attr : list) {
            Set<Attribute> attrs = new HashSet<Attribute>();
            attrs.add(attr);
            attrs.add(new Uid("1"));
            try {
                UpdateImpl.validateInput(Type.DELETE, ObjectClass.ACCOUNT, attrs);
                Assert.fail("Failed: " + attr.getName());
            } catch (IllegalArgumentException e) {
                // this is a good thing..
            }
        }
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void validateAttemptToAddNull() {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(AttributeBuilder.build("something w/ null"));
        attrs.add(new Uid("1"));
        UpdateImpl.validateInput(Type.ADD, ObjectClass.ACCOUNT, attrs);        
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void validateAttemptToDeleteNull() {
        Set<Attribute> attrs = new HashSet<Attribute>();
        attrs.add(AttributeBuilder.build("something w/ null"));
        attrs.add(new Uid("1"));
        UpdateImpl.validateInput(Type.DELETE, ObjectClass.ACCOUNT, attrs);        
    }

    @Test
    public void mergeAddAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute cattr = AttributeBuilder.build("abc", 2);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc", 2));        
        actual = up.merge(Type.ADD, changeset, base);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void mergeAddToExistingAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute battr = AttributeBuilder.build("abc", 1);
        Attribute cattr = AttributeBuilder.build("abc", 2);
        base.add(battr);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc", 1, 2));        
        actual = up.merge(Type.ADD, changeset, base);
        Assert.assertEquals(expected, actual);
    }
    
    @Test
    public void mergeDeleteNonExistentAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute cattr = AttributeBuilder.build("abc", 2);
        changeset.add(cattr);
        actual = up.merge(Type.DELETE, changeset, base);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void mergeDeleteToExistingAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute battr = AttributeBuilder.build("abc", 1, 2);
        Attribute cattr = AttributeBuilder.build("abc", 2);
        base.add(battr);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc", 1));
        actual = up.merge(Type.DELETE, changeset, base);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void mergeDeleteToExistingAttributeCompletely() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute battr = AttributeBuilder.build("abc", 1, 2);
        Attribute cattr = AttributeBuilder.build("abc", 1, 2);
        base.add(battr);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc"));
        actual = up.merge(Type.DELETE, changeset, base);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void mergeReplaceExistingAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute battr = AttributeBuilder.build("abc", 1, 2);
        Attribute cattr = AttributeBuilder.build("abc", 2);
        base.add(battr);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc", 2));
        actual = up.merge(Type.REPLACE, changeset, base);
        Assert.assertEquals(expected, actual);
    }
    
    @Test
    public void mergeReplaceNonExistentAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute cattr = AttributeBuilder.build("abc", 2);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc", 2));
        actual = up.merge(Type.REPLACE, changeset, base);
        Assert.assertEquals(expected, actual);
    }
    
    @Test
    public void mergeReplaceAttributeRemoval() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute battr = AttributeBuilder.build("abc", 1, 2);
        Attribute cattr = AttributeBuilder.build("abc");
        base.add(battr);
        changeset.add(cattr);
        expected.add(AttributeBuilder.build("abc"));
        actual = up.merge(Type.REPLACE, changeset, base);
        Assert.assertEquals(expected, actual);
    }
    
    @Test
    public void mergeReplaceSameAttribute() {
        UpdateImpl up = new UpdateImpl(null, null);
        Set<Attribute> actual;
        Uid uid = new Uid("1");
        Set<Attribute> base = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> expected = CollectionUtil.<Attribute>newSet(uid);
        Set<Attribute> changeset = CollectionUtil.<Attribute>newSet(uid);
        // attempt to add a value to an attribute..
        Attribute battr = AttributeBuilder.build("abc", 1);
        Attribute cattr = AttributeBuilder.build("abc", 1);
        base.add(battr);
        changeset.add(cattr);
        expected.add(cattr);
        actual = up.merge(Type.REPLACE, changeset, base);
        Assert.assertEquals(expected, actual);
    }
}
