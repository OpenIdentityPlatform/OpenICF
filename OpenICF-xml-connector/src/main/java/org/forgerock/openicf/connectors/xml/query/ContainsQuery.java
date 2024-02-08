package org.forgerock.openicf.connectors.xml.query;

import org.forgerock.openicf.connectors.xml.query.abstracts.QueryPart;

public class ContainsQuery implements QueryPart {

    private String prefixedName;
    private String value;
    private boolean not;

    public ContainsQuery(String prefixedName, String value, boolean not) {
        this.prefixedName = prefixedName;
        this.value = value;
        this.not = not;
    }

    public String getExpression() {
        if (not) {
            return createFalseExpression();
        } else {
            return createTrueExpression();
        }
    }

    public String createFalseExpression() {
        // format: prefixedName[contains(., 'value')]
        StringBuilder sb = new StringBuilder();

        sb.append("fn:not(");
        sb.append("$x/");
        sb.append(this.prefixedName);
        sb.append("[contains(.,");
        sb.append("'");
        sb.append(value);
        sb.append("'");
        sb.append(")]");
        sb.append(")");

        return sb.toString();
    }

    public String createTrueExpression() {
        // format: fn:not(prefixedName[contains(., 'value')])
        StringBuilder sb = new StringBuilder();

        sb.append("$x/");
        sb.append(this.prefixedName);
        sb.append("[contains(.,");
        sb.append("'");
        sb.append(value);
        sb.append("'");
        sb.append(")]");

        return sb.toString();
    }
}
