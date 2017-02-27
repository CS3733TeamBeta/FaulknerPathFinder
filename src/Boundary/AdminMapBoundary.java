package Boundary;

import Controller.Map.ViewElements.DragIconType;
import Entity.Map.*;
import javafx.geometry.Point2D;

import java.util.ArrayList;

/**
 * Created by benhylak on 2/24/17.
 */
public class AdminMapBoundary extends MapBoundary
{
    @Override
    public void changeFloor(Floor f)
    {
        super.changeFloor(f);

        edges.clear();

        for(MapNode n: nodesOnMap)
        {
            n.setOnDeleteRequested(e-> remove(n));

            for(NodeEdge edge: n.getEdges())
            {
                if (!edges.contains(edge) && nodesOnMap.contains(edge.getOtherNode(n)))
                {
                    edges.add(edge);
                }
            }
        }
    }

    /**
     * Function returns whether or not the node should be on the map, runs on floor change
     * In the parent class, this function is used to filter out connector nodes. On the admin side,
     * of course, we want to see connector nodes, hence the reason we always returns true.
     * @param n
     * @return
     */
    @Override
    protected boolean shouldBeOnMap(MapNode n)
    {
        return true;
    }

    public void newEdge(MapNode source, MapNode target)
    {
        NodeEdge edge = new NodeEdge(source, target);
        edges.add(edge);
    }

    public MapNode newNode(DragIconType type, Point2D loc)
    {
       // MapNode n;
        MapNode n = null;

        switch(type)
        {
            case Department:
            case Food:
            case Info:
            case Restroom:
            case Store:
            {
                Destination newDestination = new Destination(); //needed for lambda
                n=newDestination;
                n.setOnDeleteRequested(e-> remove(newDestination));
                break;
            }
            case Kiosk:
            {
                break;
            }
            default:
            {
                MapNode newMapNode = new MapNode(); //needed for lambda
                n = newMapNode;
                n.setOnDeleteRequested(e->remove(newMapNode));

                break;
            }
        }

        n.setType(type);
        n.setPosX(loc.getX());
        n.setPosY(loc.getY());

        if(type == DragIconType.Elevator){
            n.setIsElevator(true);
            addElevator(n);
        }
        else {
            currentFloor.addNode(n);
        }
        nodesOnMap.add(n);

        return n;
    }

    private void addElevator(MapNode n)
    {
        ArrayList<MapNode> nodesToAdd = new ArrayList<MapNode>();

        MapNode e;

        for (Floor f : currentFloor.getBuilding().getFloors())
        {
            if (!f.equals(currentFloor))
            {
                e = new MapNode();

                e.setIsElevator(true);
                e.setPos(n.getPosX(), n.getPosY());

                f.addNode(e);

                nodesToAdd.add(e);
            }
            else
            {
                e = n;
                f.addNode(e);
                nodesToAdd.add(e);
            }

            //for all elevators, add all other elevators in this elevator shaft to its edges
            for(MapNode nextNode : nodesToAdd) {
                //first make sure that we don't elevators connecting to other elevators on the same floor
                //and that we're not adding multiple of the same edge
                if(e.getMyFloor().getFloorNumber() != nextNode.getMyFloor().getFloorNumber() && !e.hasEdgeTo(nextNode)) {
                    LinkEdge edge = new LinkEdge(nextNode, e);
                    e.addEdge(edge);
                }
            }

        }
    }

    public void remove(MapNode n)
    {
        nodesOnMap.remove(n);
        currentFloor.removeNode(n);

        for(NodeEdge edge: n.getEdges())
        {
            edge.getOtherNode(n).getEdges().remove(edge);

            if(edges.contains(edge))
            {
                edges.remove(edge);
            }
        }
    }

    public void removeEdge(NodeEdge edge)
    {
        edge.getSource().getEdges().remove(edge);
        edge.getTarget().getEdges().remove(edge);

        edges.remove(edge);
    }

    public void moveNode(MapNode n, Point2D movedTo)
    {
        n.setPosX(movedTo.getX());
        n.setPosY(movedTo.getY());

        //update database

        for (NodeEdge edge : n.getEdges())
        {
            //edge.updatePosViaNode(boundary.getMapNode(n)); //@TODO
            edge.updateCost();
        }
    }
}
