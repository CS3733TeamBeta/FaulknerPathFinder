package main.Map.Boundary;


import javafx.geometry.Point2D;
import main.Application.ApplicationController;
import main.Map.Entity.*;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Created by benhylak on 2/24/17.
 */
public class AdminMapBoundary extends MapBoundary
{
    private Building currentBuilding;

    public AdminMapBoundary(Building b, Hospital h)
    {
        super(h);

        currentBuilding = b;
        currentFloor = b.getBaseFloor();
    }

    public Building getCurrentBuilding()
    {
        return currentBuilding;
    }

    /**Changes the floor and adds all of the floor's edges too**/
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

    /**
     * Creates a new edge inbetween two nodes
     *
     * @param source {link:MapNode}
     * @param target {link:MapNode}
     */
    public void newEdge(MapNode source, MapNode target)
    {
        NodeEdge edge = new NodeEdge(source, target);
        edge.updateCost();
        edges.add(edge);
        try {
            ApplicationController.getCache().getDbManager().addEdgeToDB(edge);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new node of specified type at a specified location
     *
     * @param type NodeType of the node
     * @param loc location local to the map image
     * @return
     */
    public MapNode newNode(NodeType type, Point2D loc)
    {
        MapNode n = MapNode.nodeFactory(type, loc);

        n.setOnDeleteRequested(e->remove(n));

        if(type == NodeType.Elevator){
            addElevator(n);
        }
        else {
            currentFloor.addNode(n);
            try {
                ApplicationController.getCache().getDbManager().addNodeToDB(n);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (n instanceof Destination) {
            try {
                ApplicationController.getCache().getDbManager().addDestToDB((Destination)n);
                h.addDestinations((Destination)n);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if(n instanceof Kiosk)
        {
            getHospital().getKiosks().add((Kiosk)n);
            try {
                ApplicationController.getCache().getDbManager().addKioskToDB((Kiosk)n);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        nodesOnMap.add(n);

        return n;
    }

    /**
     * Adds elevator by chainlinking it to every other elevator in line. (Artifact of having edges rather than
     * neighbors, but whatever)
     *
     * @param n Elevator node
     */
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
            try {
                ApplicationController.getCache().getDbManager().addNodeToDB(e);
            } catch (SQLException e1) {
                e1.printStackTrace();
            }

            //for all elevators, add all other elevators in this elevator shaft to its edges
            for(MapNode nextNode : nodesToAdd) {
                //first make sure that we don't elevators connecting to other elevators on the same floor
                //and that we're not adding multiple of the same edge
                if(e.getMyFloor().getFloorNumber() != nextNode.getMyFloor().getFloorNumber() && !e.hasEdgeTo(nextNode)) {
                    LinkEdge edge = new LinkEdge(nextNode, e);
                    e.addEdge(edge);
                    try {
                        ApplicationController.getCache().getDbManager().addEdgeToDB(edge);
                    } catch (SQLException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Removes a map node on the current floor and removes it from the working set
     *
     * @param n MapNode
     */
    public void remove(MapNode n)
    {
        nodesOnMap.remove(n);
        currentFloor.removeNode(n);
        try {
            ApplicationController.getCache().getDbManager().delNodeFromDB(n);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for(NodeEdge edge: n.getEdges())
        {
            edge.getOtherNode(n).getEdges().remove(edge);

            if(edges.contains(edge))
            {
                edges.remove(edge);
            }
        }
    }

    /**
     * Deletes an edge completely
     * @param edge
     */
    public void removeEdge(NodeEdge edge)
    {
        edge.getSource().getEdges().remove(edge);
        edge.getTarget().getEdges().remove(edge);

        edges.remove(edge);
        try {
            ApplicationController.getCache().getDbManager().delEdgeFromDB(edge);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set Current kiosk
     */
    public void setCurrentKiosk(Kiosk k)
    {
        h.setCurrentKiosk(k); //handles changing node type in hospital
    }

    /**
     * Moves a node to a given point
     * @param n
     * @param movedTo
     */
    public void moveNode(MapNode n, Point2D movedTo)
    {
        n.setPosX(movedTo.getX());
        n.setPosY(movedTo.getY());
        try {
            ApplicationController.getCache().getDbManager().updateNode(n);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //update database

        for (NodeEdge edge : n.getEdges())
        {
            edge.updateCost();
            try {
                ApplicationController.getCache().getDbManager().updateEdge(edge);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

}
