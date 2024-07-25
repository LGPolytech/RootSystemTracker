package io.github.rocsg.rsmlparser;

import java.awt.geom.Point2D;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// Implement Root4Parser class
public class Root4Parser implements IRootParser {
    public static int numFunctions;
    final String id;
    final String poAccession;
    public final LocalDate currentTime;
    final List<Function> functions;
    private final String label;
    private final List<Property> properties;
    private final int order;
    public List<IRootParser> children;
    protected IRootParser parent;
    private Geometry geometry;

    public Root4Parser(String id, String label, String poAccession, Root4Parser parent, int order, LocalDate currentTime) {
        this.id = id;
        this.label = label;
        this.poAccession = poAccession;
        this.properties = new ArrayList<>();
        this.functions = new ArrayList<>();
        this.order = order;
        this.parent = parent;
        this.children = new ArrayList<>();
        if (parent != null) {
            parent.addChild(this, null);
        }
        numFunctions = 2;
        this.currentTime = currentTime;
    }

    public static void collapseAll(Map<String, List<Root4Parser>> rootMap) {
        for (String key : rootMap.keySet()) {
            List<Root4Parser> roots = rootMap.get(key);
            // all these roots have not the same number of coordinates but the first ones are the same
            // We will make a mean out of the first coordinates of as much roots as possible
            double meanX = 0;
            double meanY = 0;
            int n = 0;
            for (Root4Parser root : roots) {
                if (root.geometry != null) {
                    meanX += root.geometry.get2Dpt().get(0).getX();
                    meanY += root.geometry.get2Dpt().get(0).getY();
                    n++;
                }
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getPoAccession() {
        return poAccession;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public List<IRootParser> getChildren() {
        return children;
    }

    public static List<Root4Parser> getTotalChildrenList(List<Root4Parser> roots) { // supposed to be ordered by time, increasing order
        List<Root4Parser> totalChildren = new ArrayList<>();
        for (int i = roots.size() - 1; i >= 0; i--) { // from last time to first time
            Root4Parser root = roots.get(i);
            for (IRootParser child : root.getChildren()) {
                // if the child id cannot be found in the totalChildren list, we add it
                if (totalChildren.stream().noneMatch(r -> r.getId().equals(child.getId()))) totalChildren.add((Root4Parser) child);
            }
        }
        return totalChildren;
    }

    @Override
    public void addChild(IRootParser child, IRootModelParser rootModel) {
        if (child instanceof Root4Parser) children.add(child);
        else {
            System.out.println("Only Root4Parser can be added as a child");
            //System.exit(1);
        }
    }

    @Override
    public IRootParser getParent() {
        return parent;
    }

    @Override
    public String getParentId() {
        return parent == null ? null : parent.getId();
    }

    @Override
    public String getParentLabel() {
        return parent == null ? null : parent.getLabel();
    }

    public void addProperty(Property property) {
        this.properties.add(property);
    }

    public void addFunction(Function function) {
        this.functions.add(function);
    }

    @Override
    public List<Property> getProperties() {
        return properties;
    }

    @Override
    public List<Function> getFunctions() {
        return functions;
    }

    @Override
    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    @Override
    public String toString() {
        String indent = "";
        int order = this.order;
        for (indent = ""; order > 0; order--) {
            indent += "\t";
        }
        return "\n" + indent + "Root4Parser{" +
                "\n" + indent + "\tid='" + id + '\'' +
                "\n" + indent + "\tlabel='" + label + '\'' +
                "\n" + indent + "\tproperties=" + properties +
                "\n" + indent + "\tgeometry=" + geometry +
                "\n" + indent + "\tfunctions=" + functions +
                "\n" + indent + "\tparent=" + parent +
                childIDandLbel2String("\n" + indent + "\t\t") +
                "\n" + indent + "\torder=" + order +
                '}';
    }

    private String childIDandLbel2String(String indent) {
        StringBuilder childID = new StringBuilder();
        if (children != null) {
            for (IRootParser child : children) {
                childID.append(indent).append(child.getId()).append(" : ").append(child.getLabel());
            }
        }
        return childID.toString();
    }
}
