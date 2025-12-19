package oscript.js.transpiler;

import oscript.syntaxtree.NodeChoice;
import oscript.syntaxtree.NodeListInterface;
import oscript.syntaxtree.NodeOptional;
import oscript.syntaxtree.NodeToken;

import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Small helper to recover the first available source position for a syntax tree node.
 */
final class SourceLocation {
    final int line;
    final int column;

    SourceLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    static SourceLocation fromNode(Object node) {
        NodeToken token = findFirstToken(node, new HashSet<>());
        if ((token == null) || (token.beginLine < 1) || (token.beginColumn < 1)) {
            return null;
        }
        // Convert to zero-based positions expected by source maps.
        return new SourceLocation(token.beginLine - 1, token.beginColumn - 1);
    }

    private static NodeToken findFirstToken(Object value, Set<Object> visited) {
        if ((value == null) || visited.contains(value)) {
            return null;
        }
        visited.add(value);

        if (value instanceof NodeToken) {
            return (NodeToken) value;
        }
        if (value instanceof NodeOptional) {
            NodeOptional optional = (NodeOptional) value;
            if (optional.present()) {
                return findFirstToken(optional.node, visited);
            }
            return null;
        }
        if (value instanceof NodeChoice) {
            return findFirstToken(((NodeChoice) value).choice, visited);
        }
        if (value instanceof NodeListInterface) {
            NodeListInterface list = (NodeListInterface) value;
            for (Enumeration e = list.elements(); e.hasMoreElements();) {
                NodeToken token = findFirstToken(e.nextElement(), visited);
                if (token != null) {
                    return token;
                }
            }
            return null;
        }
        if (value instanceof oscript.syntaxtree.Node) {
            for (Field field : value.getClass().getFields()) {
                try {
                    NodeToken token = findFirstToken(field.get(value), visited);
                    if (token != null) {
                        return token;
                    }
                } catch (IllegalAccessException ignored) {
                    // Fall through and continue searching other fields.
                }
            }
        }
        return null;
    }
}
