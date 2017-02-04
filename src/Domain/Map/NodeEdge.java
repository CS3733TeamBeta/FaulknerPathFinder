package Domain.Map;

/**
 * An edge that connects two nodes and has a cost (edge length)
 */
public class NodeEdge
{
    protected float cost;

    protected MapNode nodeA;
    protected MapNode nodeB;

    public NodeEdge()
    {

    }

    public float getCost() {
        return cost;
    }

    public NodeEdge(MapNode nodeA, MapNode nodeB)
    {
        this();

        this.nodeA = nodeA;
        this.nodeB = nodeB;
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
