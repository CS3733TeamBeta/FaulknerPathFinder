package main.Map.Navigation;

import main.Map.Entity.MapNode;
import main.Map.Entity.NodeEdge;
import main.Application.Exceptions.PathFindingException;
import main.Application.Exceptions.PathFindingInvalidPathException;
import main.Application.Exceptions.PathFindingNoPathException;

import java.util.Iterator;
import java.util.LinkedList;
//Testing

/**
 * Path is the path from one Destination to another
 */
public class Path implements Iterable {

    LinkedList<NodeEdge> pathEdges;
    LinkedList<MapNode> pathNodes;

    //Set to true for verbose debugging
    public boolean vFlag;

    private static final double FLOOR_HEIGHT_CONSTANT = 1.812;

    public Path() {
        System.out.println("Path constuctor must be called with fields");
    }

    /**
     * Fills this path's field with the path genereated from start to end using Astar, printing out lots of stuff if vFlag is true
     * @param start
     * @param end
     * @param vFlag
     * @throws PathFindingException
     */
    private void createPathAStar(MapNode start, MapNode end, boolean vFlag) throws PathFindingException {

        this.vFlag = vFlag;

        pathEdges = new LinkedList<NodeEdge>();
        pathNodes = new LinkedList<MapNode>();

        //Have not found the end yet.
        boolean flagDone = false;

        //Initialize sets and globals
        //openset is the list of nodes to be visited
        LinkedList<MapNode> openSet = new LinkedList<MapNode>();
        //closedset is the list of nodes already visited
        LinkedList<MapNode> closedSet = new LinkedList<MapNode>();
        //currentnode is the node we are inspecting now
        MapNode currentNode;

        //first add the starting node to the openset
        openSet.add(start);

        //Iterate through the list until either we run out of nodes to explore or we get to the exit
        while (openSet.size() > 0 && !flagDone) {

            //Get the node with the smallest best combined heuristic in the openset
            currentNode = popSmallest(openSet);

            //Add the node into the closed set to avoid errors from self-references
            closedSet.add(currentNode);

            //First check if the current node is the goal we are looking for
            if (currentNode.equals(end)) {
                reconstructPath(end);
                //Set the flag to exit the algorithm
                flagDone = true;
            }

            //If you haven't found the end,
            //For every edge in the currentNode, look at it's connected edges and add them to the openSet if they
            //ae not in either the openSet or the closedSet
            for (NodeEdge aEdge: currentNode.edges) {

                //and those edges' nodes.
                MapNode aNode = aEdge.getOtherNode(currentNode);

                //As long as you have not already visited the node, recalculate its heuristic values
                if (!closedSet.contains(aNode)) {

                    //Calculate what the F value would be
                    //the heuristic is the geometric distance from the next node to the end point
                    double tentativeNewHeuristic = findHeuristic(aNode, end);
                    //the G value is the distance we have traveled on this current path
                    double tentativeNewGValue = currentNode.getG() + aEdge.getCost();
                    //the combined heuristic is the sum of the projected distance and the travelled distance
                    //to the end node
                    double tentativeNewFValue = tentativeNewHeuristic + tentativeNewGValue;

                    //If the new F value is better than the one we have for the node, we have found a better path
                    //to that node. replace that node's values with this better parent and heuristics
                    if(tentativeNewFValue < aNode.getF()) {

                        //set the heuristic values.
                        aNode.setHeuristic(tentativeNewHeuristic);
                        aNode.setG(tentativeNewGValue);
                        aNode.setF(tentativeNewHeuristic + tentativeNewGValue);

                        //And set its parent to this node
                        aNode.setParent(aEdge);
                    }

                    //If this node isn't in the open set either, add it to the openset so we visit it later
                    if(!openSet.contains(aNode)) {
                        openSet.add(aNode);
                    }

                }
            }

        }

        //Set all nodes in the open and closed set back to default values so we can iterate through the list again
        //and find another path
        for (MapNode n: openSet) {
            n.resetTempValues();
        }
        for (MapNode n: closedSet) {
            n.resetTempValues();
        }



        //Determine pathNodes from pathEdges
        nodesFromEdges(start, end);

        //Print the edges and nodes for bugfixing
        if (vFlag) {
            printPathEdges();
            printPathNodes();
        }

        //If the loop exited without flagDone, it must have searched all nodes and found nothing
        if (!flagDone) {
            throw new PathFindingNoPathException("No valid path found", this.pathNodes, this.pathEdges);
        }

        if (!this.isValidPath()) {
            throw new PathFindingInvalidPathException("Path generated is invalid", this.pathNodes, this.pathEdges);
        }

    }
    /**
     * Fills this path's field with the path genereated from start to end using Astar, printing out lots of stuff if vFlag is true
     * @param start
     * @param end
     * @param vFlag
     * @throws PathFindingException
     */
    private void createPathRandom(MapNode start, MapNode end, boolean vFlag) throws PathFindingException {

        this.vFlag = vFlag;

        pathEdges = new LinkedList<NodeEdge>();
        pathNodes = new LinkedList<MapNode>();

        //Have not found the end yet.
        boolean flagDone = false;

        //Initialize sets and globals
        //openset is the list of nodes to be visited
        LinkedList<MapNode> openSet = new LinkedList<MapNode>();
        //closedset is the list of nodes already visited
        LinkedList<MapNode> closedSet = new LinkedList<MapNode>();
        //currentnode is the node we are inspecting now
        MapNode currentNode;

        //first add the starting node to the openset
        openSet.add(start);

        //Iterate through the list until either we run out of nodes to explore or we get to the exit
        while (openSet.size() > 0 && !flagDone) {

            //Get the node with the smallest best combined heuristic in the openset
            currentNode = popSmallest(openSet);

            //Add the node into the closed set to avoid errors from self-references
            closedSet.add(currentNode);

            //First check if the current node is the goal we are looking for
            if (currentNode.equals(end)) {
                reconstructPath(end);
                //Set the flag to exit the algorithm
                flagDone = true;
            }

            //If you haven't found the end,
            //For every edge in the currentNode, look at it's connected edges and add them to the openSet if they
            //ae not in either the openSet or the closedSet
            for (NodeEdge aEdge: currentNode.edges) {

                //and those edges' nodes.
                MapNode aNode = aEdge.getOtherNode(currentNode);

                //As long as you have not already visited the node, recalculate its heuristic values
                if (!closedSet.contains(aNode)) {

                    //Calculate what the F value would be
                    //the heuristic is the geometric distance from the next node to the end point
                    //double tentativeNewHeuristic = findHeuristic(aNode, end);
                    //the G value is the distance we have traveled on this current path
                    //double tentativeNewGValue = currentNode.getG() + aEdge.getCost();
                    //the combined heuristic is the sum of the projected distance and the travelled distance
                    //to the end node
                    //double tentativeNewFValue = tentativeNewHeuristic + tentativeNewGValue;

                    //If the new F value is better than the one we have for the node, we have found a better path
                    //to that node. replace that node's values with this better parent and heuristics
                    if(true) {

                        //set the heuristic values.
                        aNode.setHeuristic(Math.random());
                        aNode.setG(Math.random());
                        aNode.setF(Math.random());

                        //And set its parent to this node
                        aNode.setParent(aEdge);
                    }

                    //If this node isn't in the open set either, add it to the openset so we visit it later
                    if(!openSet.contains(aNode)) {
                        openSet.add(aNode);
                    }

                }
            }

        }

        //Set all nodes in the open and closed set back to default values so we can iterate through the list again
        //and find another path
        for (MapNode n: openSet) {
            n.resetTempValues();
        }
        for (MapNode n: closedSet) {
            n.resetTempValues();
        }



        //Determine pathNodes from pathEdges
        nodesFromEdges(start, end);

        //Print the edges and nodes for bugfixing
        if (vFlag) {
            printPathEdges();
            printPathNodes();
        }

        //If the loop exited without flagDone, it must have searched all nodes and found nothing
        if (!flagDone) {
            throw new PathFindingNoPathException("No valid path found", this.pathNodes, this.pathEdges);
        }

        if (!this.isValidPath()) {
            throw new PathFindingInvalidPathException("Path generated is invalid", this.pathNodes, this.pathEdges);
        }

    }
    /**
     * Fills this Path's fields with the path generated from start to end using breadth-first search, printing out stuff if vFlag is true.
     * @param start
     * @param end
     * @param vFlag
     * @throws PathFindingException
     */
    private void createPathBreadthFirst(MapNode start, MapNode end, boolean vFlag) throws PathFindingException {

        pathEdges = new LinkedList<NodeEdge>();
        pathNodes = new LinkedList<MapNode>();

        LinkedList<MapNode> openSet = new LinkedList<>();
        openSet.addFirst(start);
        boolean flagDone = false;
        LinkedList<MapNode> visitedNodes = new LinkedList<>();

        while(openSet.size() > 0 && !flagDone) {
            MapNode newNode = openSet.pop();
            visitedNodes.add(newNode);

            for (NodeEdge e : newNode.getEdges()) {
                MapNode neighbor = e.getOtherNode(newNode);
                if (!openSet.contains(neighbor) && !visitedNodes.contains(neighbor)) {
                    openSet.add(neighbor);
                    neighbor.setParent(e);
                }
            }

            if (newNode.equals(end)) {
                reconstructPath(end);
                //Set the flag to exit the algorithm
                flagDone = true;

            }
        }
        //Set all nodes in the open and closed set back to default values so we can iterate through the list again
        //and find another path
        for (MapNode n: visitedNodes) {
            n.resetTempValues();
        }
        for (MapNode n: openSet) {
            n.resetTempValues();
        }

        //Print the edges and nodes for bugfixing
        if (vFlag) {
            printPathEdges();
            printPathNodes();
        }

        //Determine pathNodes from pathEdges
        nodesFromEdges(start, end);
        //If the loop exited without flagDone, it must have searched all nodes and found nothing
        if (!flagDone) {
            throw new PathFindingNoPathException("No valid path found", this.pathNodes, this.pathEdges);
        }

        if (!this.isValidPath()) {
            throw new PathFindingInvalidPathException("Path generated is invalid", this.pathNodes, this.pathEdges);
        }
    }

    /**
     * Fills this Path's fields with the path generated from start to end using depth-first search, printing out stuff if vFlag is true.
     * @param start
     * @param end
     * @param vFlag
     * @throws PathFindingException
     */

    private void createPathDepthFirst(MapNode start, MapNode end, boolean vFlag) throws PathFindingException {

        pathEdges = new LinkedList<NodeEdge>();
        pathNodes = new LinkedList<MapNode>();

        LinkedList<MapNode> openSet = new LinkedList<>();
        openSet.addFirst(start);
        boolean flagDone = false;
        LinkedList<MapNode> visitedNodes = new LinkedList<>();

        while(openSet.size() > 0 && !flagDone) {
            MapNode newNode = openSet.pop();
            visitedNodes.add(newNode);

            for (NodeEdge e : newNode.getEdges()) {
                MapNode neighbor = e.getOtherNode(newNode);
                if (!openSet.contains(neighbor) && !visitedNodes.contains(neighbor)) {
                    openSet.addFirst(neighbor);
                    neighbor.setParent(e);
                }
            }

            if (newNode.equals(end)) {
                reconstructPath(end);
                //Set the flag to exit the algorithm
                flagDone = true;

            }
        }
        //Set all nodes in the open and closed set back to default values so we can iterate through the list again
        //and find another path
        for (MapNode n: visitedNodes) {
            n.resetTempValues();
        }
        for (MapNode n: openSet) {
            n.resetTempValues();
        }

        //Print the edges and nodes for bugfixing
        if (vFlag) {
            printPathEdges();
            printPathNodes();
        }

        //Determine pathNodes from pathEdges
        nodesFromEdges(start, end);
        //If the loop exited without flagDone, it must have searched all nodes and found nothing
        if (!flagDone) {
            throw new PathFindingNoPathException("No valid path found", this.pathNodes, this.pathEdges);
        }

        if (!this.isValidPath()) {
            throw new PathFindingInvalidPathException("Path generated is invalid", this.pathNodes, this.pathEdges);
        }
    }


    public Path(MapNode start, MapNode end) throws PathFindingException {
        this(start, end, false, "astar");
    }

    public Path(MapNode start, MapNode end, boolean vFlag) throws PathFindingException {
        this(start, end, vFlag, "astar");
    }

    /**
     * Given an ending node, reconstructs the path taken to that node, given that that path has been populated with parents.
     * @param end The final node.
     */
    private void reconstructPath(MapNode end) {
        //currentInPath is the node we are looking at now to reconstruct the path
        MapNode currentInPath = end;

        //While the current node HAS a parent, continue iterating.
        // This works because the only node without a parent in the path is the starting node.
                    while (currentInPath.getParent() != null) {

            //Stick the parent at the beginning of the list
            //we order the array like this in order to invert the list of nodes
            //the path we construct via parent is naturally the path from the end to the start.
            pathEdges.add(0, currentInPath.getParent());

            //Then go to the next node in the path
            currentInPath = currentInPath.getParent().getOtherNode(currentInPath);
        }
}

    /**
     * Constructs itself as a path between the two given nodes.
     * If vFlag is true, prints verbose logs as it does so.
     * @param start
     * @param end
     * @param vFlag
     * @throws PathFindingException
     */
    public Path(MapNode start, MapNode end, boolean vFlag, String algo) throws PathFindingException{
        this.vFlag = vFlag;
        switch (algo) {
            case "astar":
                System.out.println(start.toString());
                System.out.println(end.toString());
                createPathAStar(start, end, vFlag);
                break;
            case "breadthfirst":
                createPathBreadthFirst(start, end, vFlag);
                break;
            case "depthfirst":
                createPathDepthFirst(start, end, vFlag);
                break;
            case "random":
                createPathRandom(start, end, vFlag);
                break;
            default:
                System.out.println("Input was neither \"astar\" nor \"breadthfirst\" nor \"depthfirst\" nor \"random\", using astar");
                createPathAStar(start, end, vFlag);
        }
    }

    /**
     * Manual constructor for a path
     * @param pathEdges
     * @param pathNodes
     */
    public Path (LinkedList<NodeEdge> pathEdges, LinkedList<MapNode> pathNodes) {
        this.pathEdges = pathEdges;
        this.pathNodes = pathNodes;
    }

    public LinkedList<NodeEdge> getPathEdges() {
        return pathEdges;
    }
    public LinkedList<MapNode> getPathNodes() { return pathNodes; }

    /**
     * Given the starting and ending nodes, and knowing that pathEdges is already populated,
     * fill pathNodes from pathEdges
     * @param start
     * @param end
     */
    private void nodesFromEdges(MapNode start, MapNode end) {
        MapNode prev = start;
        for (int i = 0; i < pathEdges.size(); i++){
            pathNodes.add(prev);
            prev = pathEdges.get(i).getOtherNode(prev);

        }
        pathNodes.add(end);

    }

    //Returns true if for every node in pathNodes, it has a path to the next node.
    public boolean isValidPath() {
        boolean isValid = true;
        for(int i = 1; i < pathNodes.size() - 0; i++) {
            if ( !(pathNodes.get(i-1).hasEdgeTo(pathNodes.get(i))) ) {
                isValid = false;
            }
        }
        return isValid;
    }

    /**
     * Find the heuristic (aprox. distance) from currentNode to endNode using pythag. theorm from xpos and ypos, and
     * zpos figured out from floor and the floor height constant
     * @param currentNode
     * @param endNode
     * @return Approximate direct distance from currentNode to endNode
     */
    private static double findHeuristic(MapNode currentNode, MapNode endNode){
        //calculate the distance from one node to another via geometry
        double currentNodeX = currentNode.getPosX();
        double currentNodeY = currentNode.getPosY();
        double currentNodeZ = ( (currentNode.getMyFloor().getFloorNumber()) * FLOOR_HEIGHT_CONSTANT);
        double endNodeX = endNode.getPosX();
        double endNodeY = endNode.getPosY();
        double endNodeZ = ( (endNode.getMyFloor().getFloorNumber()) * FLOOR_HEIGHT_CONSTANT);
        return Math.sqrt(Math.pow(endNodeX - currentNodeX, 2) + Math.pow(endNodeY - currentNodeY, 2) + Math.pow(endNodeZ - currentNodeZ, 2));
    }

    //Given a list of MapNodes, removes and returns the one with the smallest F value
    private static MapNode popSmallest(LinkedList<MapNode> listOfNodes){

        //Seed smallestNode with first value
        MapNode smallestNode = listOfNodes.get(0);

        //For every node
        for(int i = 0; i < listOfNodes.size(); i++){
            //If f value is better than current smallest
            if(listOfNodes.get(i).getF() < smallestNode.getF()){
                //store this one as current smallest
                smallestNode = listOfNodes.get(i);
            }
        }
        //Remove smallest from list, and return it
        listOfNodes.remove(smallestNode);
        return smallestNode;

    }

    //Prints out all edges in this path, only used for testing
    public void printPathEdges(){
        System.out.println("Printing Edges");
        for (NodeEdge e: this.pathEdges) {
            //System.out.println("From " + Integer.toString(e.getSource().getType()) + "to " + Integer.toString(e.getTarget().getType()));
        }
    }

    //Prints out all nodes in this path, only used for testing
    public void printPathNodes(){
        System.out.println("Printing Nodes");
        for (MapNode n: this.pathNodes) {
            System.out.println("Node of id " + n.getNodeID());
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof Path) {
            return this.equals((Path) obj);
        } else {
            return false;
        }
    }

    public boolean equals(Path that) {
        LinkedList<MapNode> thisNodes = this.pathNodes;
        LinkedList<NodeEdge> thisEdges = this.pathEdges;
        LinkedList<MapNode> thatNodes = that.getPathNodes();
        LinkedList<NodeEdge> thatEdges = that.getPathEdges();
        if ((thisNodes.size() != thatNodes.size()) || (thisEdges.size() != thatEdges.size())) {
            return false;
        }
        boolean areEqual = true;
        for (int i = 0; i < thisNodes.size(); i++) {
            if (!(thisNodes.get(i).equals(thatNodes.get(i)))) {
                areEqual = false;
            }
        }
        for (int i = 0; i < thisEdges.size(); i++) {
            if (!(thisEdges.get(i).equals(thatEdges.get(i)))) {
                areEqual = false;
            }
        }
        return areEqual;
    }


    public int hashCode() {
        final int primeNum = 31;
        int totalHash = 1;
        for (MapNode n: pathNodes) {
            totalHash = totalHash * primeNum + n.hashCode();
        }
        return totalHash;
    }

    public Iterator iterator() {return pathEdges.iterator();}
}
