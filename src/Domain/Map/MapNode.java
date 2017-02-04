package Domain.Map;

import javafx.scene.image.Image;

import java.util.HashSet;

/**
 * Represents a node in a Map, connected to other nodes by NodeEdges
 */

public class MapNode
{
    int posX;
    int posY;
    int nodeID;

    Image node = null;

    Floor myFloor;
    public HashSet<NodeEdge> edges;

    public boolean equals(Object obj) {
        if (obj instanceof Destination) {
            return (this.nodeID == ((MapNode) obj).nodeID);
        }
        return false;
    }
}
