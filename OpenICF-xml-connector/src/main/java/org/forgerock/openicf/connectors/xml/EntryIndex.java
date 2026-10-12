/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.openicf.connectors.xml;

import static org.forgerock.openicf.connectors.xml.util.XmlHandlerUtil.following;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Finds entries by {@code __UID__} and {@code __NAME__} without walking the document.
 * <p>
 * Gives the same entries, in the same order, as {@code /icf:OpenICFContainer//ri:CLASS} filtered by
 * {@code $x/icf:FIELD = 'VALUE'}: entries at any depth below the container, an entry under
 * every value of its identifier child elements, values compared as they are, results in document
 * order, so the first of several duplicates comes first.
 * <p>
 * Every change to the entries must be reported through {@link #added}, {@link #removed} and
 * {@link #rekeyed}. Nothing is computed on read, so concurrent readers are safe while nobody
 * changes the index; callers change it under the handler's write lock only.
 * <p>
 * An entry inside another entry's identifier element is part of that identifier's value, but
 * changing or removing it re-keys only the inner entry: the outer one keeps its old key until the
 * next {@link #rebuild}. The connector never writes that shape; only a hand-edited file has it.
 */
final class EntryIndex {

    private final String icfNamespace;
    private final String riNamespace;
    private final Set<String> objectClasses;

    private final Map<Element, Indexed> entries = new IdentityHashMap<Element, Indexed>();
    private final Map<String, TreeMap<Long, Element>> byClass = new HashMap<String, TreeMap<Long, Element>>();
    private final Map<Key, List<Indexed>> byIdentifier = new HashMap<Key, List<Indexed>>();
    private long nextOrder;
    /** Whether the last rebuilt document has the container as its root; otherwise nothing is indexed. */
    private boolean containerRoot;

    EntryIndex(String icfNamespace, String riNamespace, Set<String> objectClasses) {
        this.icfNamespace = icfNamespace;
        this.riNamespace = riNamespace;
        this.objectClasses = new HashSet<String>(objectClasses);
    }

    /** Replaces the content of the index with the entries of {@code document}. */
    void rebuild(Document document) {
        entries.clear();
        byClass.clear();
        byIdentifier.clear();
        nextOrder = 0;
        containerRoot = false;
        Element root = document.getDocumentElement();
        if (root == null || !icfNamespace.equals(root.getNamespaceURI())
                || !XMLHandlerImpl.ICF_CONTAINER_TAG.equals(root.getLocalName())) {
            return;
        }
        containerRoot = true;
        addAll(root);
    }

    Element first(String objectClass, String field, String value) {
        List<Indexed> list = byIdentifier.get(new Key(objectClass, field, value));
        return list == null ? null : list.get(0).element;
    }

    List<Element> find(String objectClass, String field, String value) {
        List<Indexed> list = byIdentifier.get(new Key(objectClass, field, value));
        if (list == null) {
            return Collections.emptyList();
        }
        List<Element> result = new ArrayList<Element>(list.size());
        for (Indexed indexed : list) {
            result.add(indexed.element);
        }
        return result;
    }

    List<Element> all(String objectClass) {
        TreeMap<Long, Element> ordered = byClass.get(objectClass);
        return ordered == null ? Collections.<Element>emptyList() : new ArrayList<Element>(ordered.values());
    }

    /** Records an entry just appended as the last child of the container, and every entry inside it. */
    void added(Element entry) {
        if (containerRoot) {
            addAll(entry);
        }
    }

    /** Forgets {@code subtree} if it is an entry, and every entry inside it. */
    void removed(Node subtree) {
        for (Element entry : entriesIn(subtree)) {
            Indexed indexed = entries.remove(entry);
            if (indexed != null) {
                byClass.get(indexed.objectClass).remove(indexed.order);
                unlink(indexed);
            }
        }
    }

    /**
     * Reads the identifiers of an entry whose children have changed; its place in document order stays.
     * A new entry inside it, such as a value element named like an object class, has no place in
     * document order yet, so the whole index is rebuilt then.
     */
    void rekeyed(Element entry) {
        Indexed indexed = entries.get(entry);
        if (indexed == null) {
            return;
        }
        if (holdsUnindexedEntry(entry)) {
            rebuild(entry.getOwnerDocument());
            return;
        }
        unlink(indexed);
        indexed.keys = keysOf(entry, indexed.objectClass);
        link(indexed);
    }

    private boolean isEntry(Node node) {
        return riNamespace.equals(node.getNamespaceURI()) && objectClasses.contains(node.getLocalName());
    }

    /** Indexes {@code subtree} if it is an entry, and every entry inside it, in document order. */
    private void addAll(Node subtree) {
        for (Element entry : entriesIn(subtree)) {
            add(entry);
        }
    }

    private boolean holdsUnindexedEntry(Node subtree) {
        for (Element entry : entriesIn(subtree)) {
            if (!entries.containsKey(entry)) {
                return true;
            }
        }
        return false;
    }

    /** {@code subtree} if it is an entry, and every entry inside it, in document order. */
    private List<Element> entriesIn(Node subtree) {
        List<Element> found = new ArrayList<Element>();
        Node node = subtree;
        while (node != null) {
            Node child = null;
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                if (isEntry(node)) {
                    found.add((Element) node);
                }
                child = node.getFirstChild();
            }
            node = child != null ? child : following(node, subtree);
        }
        return found;
    }

    private void add(Element entry) {
        String objectClass = entry.getLocalName();
        Indexed indexed = new Indexed(entry, objectClass, nextOrder++, keysOf(entry, objectClass));
        entries.put(entry, indexed);
        TreeMap<Long, Element> ordered = byClass.get(objectClass);
        if (ordered == null) {
            ordered = new TreeMap<Long, Element>();
            byClass.put(objectClass, ordered);
        }
        ordered.put(indexed.order, entry);
        link(indexed);
    }

    /** Identifier child elements only: {@code $x/icf:FIELD} selects children, not descendants. */
    private Set<Key> keysOf(Element entry, String objectClass) {
        Set<Key> keys = new LinkedHashSet<Key>();
        for (Node child = entry.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && icfNamespace.equals(child.getNamespaceURI())) {
                String field = child.getLocalName();
                if (Uid.NAME.equals(field) || Name.NAME.equals(field)) {
                    keys.add(new Key(objectClass, field, child.getTextContent()));
                }
            }
        }
        return keys;
    }

    private void link(Indexed indexed) {
        for (Key key : indexed.keys) {
            List<Indexed> list = byIdentifier.get(key);
            if (list == null) {
                list = new ArrayList<Indexed>(1);
                byIdentifier.put(key, list);
            }
            int i = list.size();
            while (i > 0 && list.get(i - 1).order > indexed.order) {
                i--;
            }
            list.add(i, indexed);
        }
    }

    private void unlink(Indexed indexed) {
        for (Key key : indexed.keys) {
            List<Indexed> list = byIdentifier.get(key);
            list.remove(indexed);
            if (list.isEmpty()) {
                byIdentifier.remove(key);
            }
        }
    }

    private static final class Indexed {
        final Element element;
        final String objectClass;
        final long order;
        Set<Key> keys;

        Indexed(Element element, String objectClass, long order, Set<Key> keys) {
            this.element = element;
            this.objectClass = objectClass;
            this.order = order;
            this.keys = keys;
        }
    }

    private static final class Key {
        final String objectClass;
        final String field;
        final String value;

        Key(String objectClass, String field, String value) {
            this.objectClass = objectClass;
            this.field = field;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key other = (Key) o;
            return objectClass.equals(other.objectClass) && field.equals(other.field) && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectClass, field, value);
        }
    }
}
