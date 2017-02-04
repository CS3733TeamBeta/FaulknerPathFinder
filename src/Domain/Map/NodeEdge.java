package Domain.Map;

/**
 * An edge that connects two nodes and has a cost (edge length)
 */
public class NodeEdge
{
    public static class EdgeEndPoint
    {

    }

    protected float cost;

    protected MapNode nodeA;
    protected MapNode nodeB;

    protected EdgeEndPoint a;
    protected EdgeEndPoint b;

    public NodeEdge()
    {

    }

    public float getCost() {
        return cost;
    }

    public MapNode getNodeA() {
        return nodeA;
    }
    public MapNode getNodeB() {
        return nodeB;
    }

    public NodeEdge(MapNode nodeA, MapNode nodeB)
    {
        this();

        this.nodeA = nodeA;
        this.nodeB = nodeB;
    }

    public NodeEdge(MapNode nodeA, MapNode nodeB, float cost) {
        this();

        this.nodeA = nodeA;
        this.nodeB = nodeB;
        this.cost = cost;
        nodeA.addEdge(this);
        nodeB.addEdge(this);

    }


    public MapNode getOtherNode(MapNode n)
    {
        if(nodeA.equals(n))
        {
            return nodeB;
        }
        else return nodeA;
    }
}
