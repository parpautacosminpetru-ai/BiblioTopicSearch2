package ro.bibliotopicsearch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable after compilation. Matching cost depends on OCR text and matching keys,
 * not on comparing every word with every term in every selected map. */
final class TermIndex<T> {
    private static final class Node<T> {
        final Map<Character, Node<T>> children = new HashMap<>();
        List<T> values;
    }

    private final Node<T> root = new Node<>();

    void add(String key, T value) {
        if (key.isEmpty()) return;
        Node<T> node = root;
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            Node<T> next = node.children.get(ch);
            if (next == null) {
                next = new Node<>();
                node.children.put(ch, next);
            }
            node = next;
        }
        if (node.values == null) node.values = new ArrayList<>();
        node.values.add(value);
    }

    List<T> exact(String text) {
        Node<T> node = root;
        for (int i = 0; i < text.length(); i++) {
            node = node.children.get(text.charAt(i));
            if (node == null) return Collections.emptyList();
        }
        return node.values == null ? Collections.emptyList() : node.values;
    }

    List<T> prefix(String text) {
        List<T> out = new ArrayList<>();
        Node<T> node = root;
        for (int i = 0; i < text.length(); i++) {
            node = node.children.get(text.charAt(i));
            if (node == null) break;
            if (node.values != null) out.addAll(node.values);
        }
        return out;
    }

    List<T> contains(String text) {
        Set<T> out = new LinkedHashSet<>();
        for (int start = 0; start < text.length(); start++) {
            Node<T> node = root;
            for (int i = start; i < text.length(); i++) {
                node = node.children.get(text.charAt(i));
                if (node == null) break;
                if (node.values != null) out.addAll(node.values);
            }
        }
        return new ArrayList<>(out);
    }
}
