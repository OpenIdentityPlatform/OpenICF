package org.forgerock.openicf.csvfile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.HashSet;
import java.util.Set;

import org.forgerock.openicf.csvfile.util.TestUtils;
import org.identityconnectors.common.Base64;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

/**
 * @author Viliam Repan (lazyman)
 */
public class MultivalueTest {

    private CSVFileConfiguration createConfig() throws Exception {
        CSVFileConfiguration config = new CSVFileConfiguration();
        config.setFilePath(TestUtils.getTestFile("multivalue.csv"));
        config.setUniqueAttribute("id");
        config.setPasswordAttribute("password");
        config.setNameAttribute("id");
        config.setUsingMultivalue(true);
        config.setMultivalueDelimiter(";");
        config.setFieldDelimiter(",");
        config.setValueQualifier("\"");
        config.setEncoding("utf-8");

        return config;
    }

    @Test
    public void getMultivalue() throws Exception {
        CSVFileConfiguration config = createConfig();
        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        ResultsHandler handler = new ResultsHandler() {

            public boolean handle(ConnectorObject obj) {
                Attribute groups = obj.getAttributeByName("groups");
                assertNotNull(groups, "groups attribute is null.");
                assertNotNull(groups.getValue(), "group values list is null.");
                assertEquals(groups.getValue().size(), 2, "group values count is wrong");

                return true;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);
    }

    @Test
    public void setMultivalue() throws Exception {
        CSVFileConfiguration config = createConfig();
        CSVFileConnector connector = new CSVFileConnector();
        connector.init(config);

        final Uid uid = connector.create(ObjectClass.ACCOUNT, createAttributeSet(), null);

        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(AttributeBuilder.build("groups", "group1"));
        connector.addAttributeValues(ObjectClass.ACCOUNT, uid, attributes, null);

        ResultsHandler handler = new ResultsHandler() {

            public boolean handle(ConnectorObject obj) {
                if (!obj.getUid().equals(uid)) {
                    return true;
                }

                Attribute groups = obj.getAttributeByName("groups");
                assertNotNull(groups, "groups attribute is null.");
                assertNotNull(groups.getValue(), "group values list is null.");
                assertEquals(groups.getValue().size(), 1, "group values count is wrong: "
                        + groups.getValue());
                assertEquals(groups.getValue().get(0), "group1", "group value must be 'group1'");

                return true;
            }
        };
        connector.executeQuery(ObjectClass.ACCOUNT, null, handler, null);

    }

    private Set<Attribute> createAttributeSet() {
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(new Name("joe"));
        attributes.add(createAttribute("firstname", "foo"));
        attributes.add(createAttribute("lastname", "bar"));
        attributes.add(createAttribute("disabled", "false"));
        attributes.add(AttributeBuilder.buildPassword(new GuardedString(Base64.encode(
                "asdf".getBytes()).toCharArray())));

        return attributes;
    }

    private Attribute createAttribute(String name, Object... values) {
        return AttributeBuilder.build(name, values);
    }
}
