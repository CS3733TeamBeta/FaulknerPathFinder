package main.Application.Events;

import main.Map.Entity.NodeEdge;

/**
 * Created by benhylak on 2/4/17.
 */
public class EdgeCompleteEvent
{
    NodeEdge edge;

    public EdgeCompleteEvent(NodeEdge edge)
    {
        this.edge =edge;
    }

    public NodeEdge getNodeEdge()
    {
        return edge;
    }
}
