package nextjournal.markdown.impl.types;

// rebuild with:
// javac -source 8 -target 1.8 src/nextjournal/markdown/impl/types/CustomNode.java

public interface CustomNode {

    public Object setLiteral(Object v);
    public Object getLiteral();
    public Object nodeType();

}
