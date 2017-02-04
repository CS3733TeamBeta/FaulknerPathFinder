package Domain.Map;

/**
 * An edge that connects two nodes and has a cost (edge length)
 */
public class NodeEdge
{
    protected double cost;

    protected MapNode nodeA;
    protected MapNode nodeB;

    public NodeEdge() {
    }

    public NodeEdge(MapNode nodeA, MapNode nodeB, double cost) {
        this();
        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.cost = cost;
        nodeA.addEdge(this);
        nodeB.addEdge(this);

    }

    public double getCost() {
        return cost;
    }
    public MapNode getNodeA() {
        return nodeA;
    }
    public MapNode getNodeB() {
        return nodeB;
    }

    //Returns the node connected to this edge that isn't passed in
    public MapNode getOtherNode(MapNode n)
    {
        if(nodeA.equals(n))
        {
            return nodeB;
        }
        else return nodeA;
    }
}
