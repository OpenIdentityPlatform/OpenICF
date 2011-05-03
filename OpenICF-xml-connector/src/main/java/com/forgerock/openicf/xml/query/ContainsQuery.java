package com.forgerock.openicf.xml.query;

import com.forgerock.openicf.xml.query.abstracts.QueryPart;

public class ContainsQuery implements QueryPart {

    private String prefixedName;
    private String value;

    public ContainsQuery(String prefixedName, String value) {
        this.prefixedName = prefixedName;
        this.value = value;
    }

    public String getExpression() {
        // format: prefixedName[contains(., 'value')]
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
