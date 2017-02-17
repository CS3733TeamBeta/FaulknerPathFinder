package Controller.Admin;

import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import Controller.AbstractController;
import Controller.DragDropMain;
import Controller.Main;
import Controller.SceneSwitcher;
import Domain.Map.*;
import Domain.ViewElements.*;
import Domain.ViewElements.Events.EdgeCompleteEvent;
import Domain.ViewElements.Events.EdgeCompleteEventHandler;
import Model.DataSourceClasses.MapTreeItem;
import Model.DataSourceClasses.TreeViewWithItems;
import Model.MapEditorModel;
import Model.MapModel;
//import apple.laf.JRSUIUtils;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import jfxtras.labs.util.event.MouseControlUtil;
import javafx.scene.input.KeyCode;
import org.controlsfx.control.PopOver;

public class MapEditorController extends AbstractController {

	@FXML SplitPane base_pane;
	@FXML AnchorPane mapPane;
	@FXML HBox bottom_bar;
	@FXML AnchorPane root_pane;
	@FXML ImageView mapImage;

	@FXML
	Button newBuildingButton;

	@FXML
	Button newFloorButton;

	@FXML
	private TabPane BuildingTabPane;

	private DragIcon mDragOverIcon = null;

	private EventHandler<DragEvent> onIconDragOverRoot = null;
	private EventHandler<DragEvent> onIconDragDropped = null;
	private EventHandler<DragEvent> onIconDragOverRightPane = null;
	private MapEditorModel model;

	NodeEdge drawingEdge;

	public Tab addBuilding(Building b)
	{
		if(b==null)
		{
			b = new Building("Building " + model.getBuildingCount());
		}

		final Label label = new Label(b.getName());
		final Tab tab = new Tab();
		tab.setGraphic(label);
		final TextField textField = new TextField();

		label.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount()==2)
				{
					textField.setText(label.getText());
					tab.setGraphic(textField);
					textField.selectAll();
					textField.requestFocus();
				}
				else
				{

				}
			}
		});


		textField.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				label.setText(textField.getText());
				tab.setGraphic(label);
			}
		});


		textField.focusedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable,
								Boolean oldValue, Boolean newValue) {
				if (! newValue) {
					label.setText(textField.getText());
					tab.setGraphic(label);
				}
			}
		});

		TreeViewWithItems<MapTreeItem> tV = new TreeViewWithItems<MapTreeItem>();

		tV.setRoot(new TreeItem<MapTreeItem>(null));
		tV.setShowRoot(false);

		tab.setContent(tV);

		model.addBuilding(b, tab);
		BuildingTabPane.getTabs().add(tab);

		return tab;
	}

	@FXML
	void onNewBuilding(ActionEvent event)
	{
		addBuilding(null);
	}

	/**
	 * Runs when "New Floor" is clicked
	 *
	 * @param event from button click
	 */
	@FXML
	void onNewFloor(ActionEvent event)
	{
		TreeViewWithItems<MapTreeItem> treeView = (TreeViewWithItems<MapTreeItem>)BuildingTabPane.getSelectionModel().getSelectedItem().getContent();

		Building b = model.getBuildingFromTab(BuildingTabPane.getSelectionModel().getSelectedItem());

		Floor f = b.newFloor(); //makes new floor

		treeView.getRoot().getChildren().add(makeTreeItem(f));
	}

	public TreeItem<MapTreeItem> makeTreeItem(Object o)
	{
		//TreeItem<Object> treeItem = new TreeItem<Object>(o);

		MapTreeItem treeObj = new MapTreeItem(o);

		//treeItem.setValue(o);

		TreeItem<MapTreeItem> treeItem = new TreeItem<>(treeObj);

		treeItem.setExpanded(true);

		return treeItem;
	}

	public void changeFloorSelection(Floor f)
	{
		model.setCurrentFloor(f);

		//change image
		//clear nodes
		//load nodes


	}

	public void refreshNodePositions()
	{
		for (MapNode n : model.getCurrentFloor().getFloorNodes())
		{
			DragIcon icon = (DragIcon)n.getNodeToDisplay();

			Point2D newPoint = new Point2D(icon.getLayoutX() + icon.getBoundsInLocal().getWidth() / 2,
					icon.getLayoutY() + icon.getBoundsInLocal().getHeight() / 2);

			n.setPosX((newPoint.getX()));
			n.setPosY((newPoint.getY()));
		}
	}

	public MapEditorController() {

		model = new MapEditorModel();

		//Runs once the edge is drawn from one node to another
		//connects the two, sends sources, positions them etc.
		model.addEdgeCompleteHandler(event->
		{
			NodeEdge completedEdge = drawingEdge;

			addHandlersToEdge(completedEdge);

			MapNode sourceNode = event.getNodeEdge().getSource();
			MapNode targetNode = event.getNodeEdge().getTarget();

			model.addMapEdge(drawingEdge);

			mapPane.setOnMouseMoved(null);

			drawingEdge.setSource(sourceNode);
			drawingEdge.setTarget(targetNode);

			sourceNode.addEdge(drawingEdge); // add the current drawing edge to the list of this node's edges
			targetNode.addEdge(drawingEdge); // add the current drawing edge to the list of this node's edges

			makeMapNodeDraggable(sourceNode);
			makeMapNodeDraggable(targetNode);

			drawingEdge.getNodeToDisplay().toBack(); //send drawing edge to back
			drawingEdge = null;

			sourceNode.toFront();
			sourceNode.toFront();
			mapImage.toBack();

		});
	}

	protected void renderInitialMap()
	{
		if(DragDropMain.mvm != null) {
			System.out.println("Begin render...");
			//System.out.println("Nodes to add: " + DragDropMain.mvm.getCurrentFloor().getFloorNodes().size());
			//import a model if one exists
			model.setCurrentFloor(DragDropMain.mvm.getCurrentFloor());
		}
		else if(Main.mvm != null) {
			model.setCurrentFloor(Main.mvm.getCurrentFloor());
		}

		if(DragDropMain.mvm != null || Main.mvm != null){
			//and then set all the existing nodes up
			HashSet<NodeEdge> collectedEdges = new HashSet<NodeEdge>();

			for(MapNode n : model.getCurrentFloor().getFloorNodes())
			{
				//System.out.println("Adding node");
				addToAdminMap(n);

				for(NodeEdge edge: n.getEdges())
				{
					if(!collectedEdges.contains(edge)) collectedEdges.add(edge);
				}
			}


			for(NodeEdge edge : collectedEdges)
			{
				addHandlersToEdge(edge);
				mapPane.getChildren().add(edge.getNodeToDisplay());

				MapNode source = edge.getSource();
				MapNode target = edge.getTarget();

				//@TODO BUG WITH SOURCE DATA, I SHOULDNT HAVE TO DO THIS

				if(!mapPane.getChildren().contains(source.getNodeToDisplay()))
				{
					addToAdminMap(source);
				}

				if(!mapPane.getChildren().contains(target.getNodeToDisplay()))
				{
					addToAdminMap(target);
				}

				edge.updatePosViaNode(source);
				edge.updatePosViaNode(target);

				edge.toBack();
				source.toFront();
				target.toFront();

				mapImage.toBack();
			}
		}
		else{
			model = new MapEditorModel();
		}

	}

	/**Adds handlers to handle edge deletion mostly
	 *
	 * @param edge to add handlers to
	 */
	public void addHandlersToEdge(NodeEdge edge)
	{
		edge.getNodeToDisplay().setOnMouseEntered(deEvent->{
			if (edge != null)
			{
				edge.getEdgeLine().setStroke(Color.RED);
			}
		});

		edge.getNodeToDisplay().setOnMouseExited(deEvent->{
			if (edge != null) {
				edge.getEdgeLine().setStroke(Color.BLACK);
			}
		});

		edge.getNodeToDisplay().setOnMouseClicked(deEvent->{
			if (edge != null) {
				if (deEvent.getClickCount() == 2) {
					edge.getSource().getEdges().remove(edge);
					edge.getTarget().getEdges().remove(edge);
					mapPane.getChildren().remove(edge.getNodeToDisplay()); //remove from the right pane
					model.removeMapEdge(edge);
				}
			}
		});
	}

	public void onEdgeComplete() {
		System.out.println("Edge complete");
		for(EdgeCompleteEventHandler handler : model.getEdgeCompleteHandlers())
		{
			if(!model.getCurrentFloor().getFloorEdges().contains(drawingEdge)){
				model.getCurrentFloor().getFloorEdges().add(drawingEdge);
			}
			handler.handle(new EdgeCompleteEvent(drawingEdge));
		}
	}

	@FXML
	private void initialize() {

		renderInitialMap();

		//BuildingTabPane.getTabs().add(createEditableTab("Building 3"));

		//Add one icon that will be used for the drag-drop process
		//This is added as a child to the root anchorpane so it can be visible
		//on both sides of the split pane.
		mDragOverIcon = new DragIcon();
		mDragOverIcon.setVisible(false);
		mDragOverIcon.setOpacity(0.65);

		root_pane.getChildren().add(mDragOverIcon);
		
		//populate left pane with multiple colored icons for testing
		for (int i = 0; i < DragIconType.values().length; i++)
		{
			DragIcon icn = new DragIcon();

			icn.setStyle("-fx-background-size: 64 64");

			addDragDetection(icn);
			icn.setType(DragIconType.values()[i]);

			if (icn.getType().equals(DragIconType.connector))
			{
				//System.out.println("Adding Connector");
				icn.setStyle("-fx-background-size: 30 30");
			}

			model.addSideBarIcon(icn);
			bottom_bar.getChildren().add(icn);
		}

		buildDragHandlers();

		/*
		 * Adds buildings to tab pane.
		 */
		for(Building b : model.getHospital().getBuildings())
		{
			Tab t = addBuilding(b);

			TreeViewWithItems<MapTreeItem> treeView = (TreeViewWithItems<MapTreeItem>)t.getContent();

			treeView.getSelectionModel().selectedItemProperty().addListener((observable, oldvalue, newvalue) -> {
				if(newvalue.getValue().getValue() instanceof Floor)
				{
					changeFloorSelection((Floor)newvalue.getValue().getValue());
				}
				else
				{
					changeFloorSelection((Floor)(newvalue.getParent().getValue().getValue()));
				}
			});

			for(Floor f: b.getFloors())
			{
				treeView.getRoot().getChildren().add(makeTreeItem(f));
			}

			treeView.getRoot().getChildren().sort(Comparator.comparing(o -> o.toString()));
		}


		getCurrentTreeView().getSelectionModel().select(0);
	}

	/**
	 * Handler to be called when node is dragged... updates end point of any edges connected to it
	 * Also makes sure the mapnode location gets updated on a drag
	 *
	 * @param n node to keep in sync
	 * @return event handler for mouse event that updates positions of lines when triggered
	 */
	private static void makeMapNodeDraggable (MapNode n)
	{
		MouseControlUtil.makeDraggable(n.getNodeToDisplay(), //could be used to track node and update line
				event ->
				{
					for (NodeEdge edge : n.getEdges())
					{
						edge.updatePosViaNode(n);
					}

					n.setPosX(event.getSceneX());
					n.setPosY(event.getSceneY());

					//System.out.println("Node " + n.getIconType().name() + " moved to (X: "+ event.getSceneX() + ", Y: " + event.getSceneY() + ")");

				},
				null);
	}

	private void addDragDetection(DragIcon dragIcon) {
		
		dragIcon.setOnDragDetected (new EventHandler <MouseEvent> () {

			@Override
			public void handle(MouseEvent event) {

				// set drag event handlers on their respective objects
				base_pane.setOnDragOver(onIconDragOverRoot);
				mapPane.setOnDragOver(onIconDragOverRightPane);
				mapPane.setOnDragDropped(onIconDragDropped);
				
				// get a reference to the clicked DragIcon object
				DragIcon icn = (DragIcon) event.getSource();
				
				//begin drag ops
				mDragOverIcon.setType(icn.getType());
				mDragOverIcon.relocateToPoint(new Point2D (event.getSceneX(), event.getSceneY()));
            
				ClipboardContent content = new ClipboardContent();
				DragContainer container = new DragContainer();
				
				container.addData ("type", mDragOverIcon.getType().toString());
				content.put(DragContainer.AddNode, container);

				mDragOverIcon.startDragAndDrop (TransferMode.ANY).setContent(content);
				mDragOverIcon.setVisible(false);
				mDragOverIcon.setMouseTransparent(true);
				event.consume();					
			}
		});
	}

	private void buildDragHandlers()
	{

		//drag over transition to move widget form left pane to right pane
		onIconDragOverRoot = new EventHandler<DragEvent>()
		{

			@Override
			public void handle(DragEvent event)
			{

				Point2D p = mapPane.sceneToLocal(event.getSceneX(), event.getSceneY());

				//turn on transfer mode and track in the right-pane's context 
				//if (and only if) the mouse cursor falls within the right pane's bounds.
				if (!mapPane.boundsInLocalProperty().get().contains(p))
				{
					event.acceptTransferModes(TransferMode.ANY);
					mDragOverIcon.relocateToPoint(new Point2D(event.getSceneX(), event.getSceneY()));
					return;
				}

				event.consume();
			}
		};

		onIconDragOverRightPane = new EventHandler<DragEvent>()
		{

			@Override
			public void handle(DragEvent event)
			{
				event.acceptTransferModes(TransferMode.ANY);

				//convert the mouse coordinates to scene coordinates,
				//then convert back to coordinates that are relative to 
				//the parent of mDragIcon.  Since mDragIcon is a child of the root
				//pane, coodinates must be in the root pane's coordinate system to work
				//properly.

				mDragOverIcon.relocateToPoint(new Point2D(event.getSceneX(), event.getSceneY()));
				event.consume();
			}
		};

		onIconDragDropped = new EventHandler<DragEvent>()
		{
			@Override
			public void handle(DragEvent event)
			{
				System.out.println("Node added");

				DragContainer container = (DragContainer) event.getDragboard().getContent(DragContainer.AddNode);

				container.addData("scene_coords", new Point2D(event.getSceneX(), event.getSceneY()));

				ClipboardContent content = new ClipboardContent();
				content.put(DragContainer.AddNode, container);

				event.getDragboard().setContent(content);
				event.setDropCompleted(true);

			}
		};

		root_pane.setOnDragDone(new EventHandler<DragEvent>()
		{

			@Override
			public void handle(DragEvent event)
			{
				System.out.println("test");
				mapPane.removeEventHandler(DragEvent.DRAG_OVER, onIconDragOverRightPane); //remove the event handlers created on drag start
				mapPane.removeEventHandler(DragEvent.DRAG_DROPPED, onIconDragDropped);
				base_pane.removeEventHandler(DragEvent.DRAG_OVER, onIconDragOverRoot);

				mDragOverIcon.setVisible(false);

				DragContainer container = (DragContainer) event.getDragboard().getContent(DragContainer.AddNode); //information from the drop

				if (container != null)
				{

					if (container.getValue("scene_coords") != null)
					{
						MapNode droppedNode;

						droppedNode = DragIcon.constructMapNodeFromType((DragIconType.valueOf(container.getValue("type"))));
						droppedNode.setType(DragIconType.valueOf(container.getValue("type"))); //set the type

						Point2D cursorPoint = container.getValue("scene_coords"); //cursor point

						droppedNode.setPosX(cursorPoint.getX()-12.5); //offset because mouse drag and pictures should start from upper corner
						droppedNode.setPosY(cursorPoint.getY()-12.5);

						addToAdminMap(droppedNode);
					}
					event.consume();
				}
			}

			;
		});
	}

	/**
	 * Adds a fresh node to the admin map, handles event handler creation, layering etc.
	 * @param mapNode
	 */
	public void addToAdminMap(MapNode mapNode)
	{
		addEventHandlersToNode(mapNode);

		mapPane.getChildren().add(mapNode.getNodeToDisplay()); //add to right panes children

		model.addMapNode(mapNode); //add node to model

		mapNode.toFront(); //send the node to the front

		if (!model.getCurrentFloor().getFloorNodes().contains(mapNode))
		{
			System.out.println("Node " + mapNode.getIconType().name() + " added to: " + mapNode.getPosX() + " " + mapNode.getPosY());
			mapNode.setFloor(model.getCurrentFloor());
			model.getCurrentFloor().getFloorNodes().add(mapNode);
		}

		((DragIcon) mapNode.getNodeToDisplay()).relocateToPoint(new Point2D(mapNode.getPosX(),
				mapNode.getPosY())); //placed by upper left corner	((DragIcon) mapNode.getNodeToDisplay()).relocateToPoint(new Point2D(mapNode.getPosX()-32,
						/* Build up event handlers for this droppedNode */

		if(mapNode instanceof Destination && !((Destination)mapNode).getInfo().getName().isEmpty())
		{
			TreeViewWithItems<MapTreeItem> treeView = (TreeViewWithItems<MapTreeItem>) BuildingTabPane.getSelectionModel().getSelectedItem().getContent();

			TreeItem<MapTreeItem> selectedTreeItem = treeView.getSelectionModel().getSelectedItem();

			MapTreeItem selectedObject = selectedTreeItem.getValue();

			if(selectedObject!=null)
			{
				TreeItem<MapTreeItem> floorTreeItem;

				//selectedObject.
				if(selectedObject.getValue() instanceof Floor)
				{
					floorTreeItem = selectedTreeItem;
				}
				else
				{
					floorTreeItem = selectedTreeItem.getParent();
				}

				((Floor)floorTreeItem.getValue().getValue()).addNode(mapNode);
				floorTreeItem.getChildren().add(makeTreeItem(mapNode));
			}

			treeView.refresh();
		}
	}

	public TreeViewWithItems<MapTreeItem> getCurrentTreeView()
	{
		return (TreeViewWithItems<MapTreeItem>)BuildingTabPane.getSelectionModel().getSelectedItem().getContent();
	}
	/**
	 * Adds all of the event handlers to handle dragging, edge creation, deletion etc.
	 * Needs to be called on newly constructed nodes to interact properly with map
	 *
	 * @param mapNode
	 */
	public void addEventHandlersToNode(MapNode mapNode)
	{
		makeMapNodeDraggable(mapNode); //make it draggable

		/***Handles deletion from a popup menu**/
		mapNode.setOnDeleteRequested(event -> {
			removeNode(event.getSource());
		});

		mapNode.getNodeToDisplay().setOnMouseClicked(ev -> {

			if(ev.getButton() == MouseButton.SECONDARY) //if right click
			{
				PopOver popOver = mapNode.getEditPopover();

				/***If the name is set, at it to the tree*/
				popOver.setOnHiding(event -> {
					if(mapNode instanceof Destination && !((Destination)mapNode).getInfo().getName().isEmpty())
					{
						getCurrentTreeView().refresh();
					}
				});

				popOver.show(mapNode.getNodeToDisplay(),
						ev.getScreenX(),
						ev.getScreenY());


				//removeNode(droppedNode);
			}
			else if (ev.getButton() == MouseButton.PRIMARY) { // deal with other types of mouse clicks
				if(ev.getClickCount() == 2) // double click
				{
					onStartEdgeDrawing(mapNode);

				}else{
					System.out.println("Node " + mapNode.getNodeUID() + " moved to: " + mapNode.getPosX() + " " +  mapNode.getPosY());
				}
			}

			if (drawingEdge!=null && !drawingEdge.getSource().equals(mapNode))
			{
				drawingEdge.setTarget(mapNode);
				onEdgeComplete();
			}
		});

		mapNode.getNodeToDisplay().setOnMouseEntered(ev->
		{
			mapNode.getNodeToDisplay().setOpacity(.65);
		});

		mapNode.getNodeToDisplay().setOnMouseExited(ev->
		{
			mapNode.getNodeToDisplay().setOpacity(1);
		});
	}

	public void onStartEdgeDrawing(MapNode mapNode)
	{
		if(drawingEdge != null) //if currently drawing... handles case of right clicking to start a new node
		{
			if(mapPane.getChildren().contains(drawingEdge.getNodeToDisplay())) //and the right pane has the drawing edge as child
			{
				mapPane.getChildren().remove(drawingEdge.getNodeToDisplay()); //remove from the right pane
			}
		}

		drawingEdge = new NodeEdge();
		drawingEdge.setSource(mapNode);

		mapPane.getChildren().add(drawingEdge.getNodeToDisplay());
		drawingEdge.toBack();
		mapImage.toBack();

		mapNode.getNodeToDisplay().setOnMouseDragEntered(null); //sets drag handlers to null so they can't be repositioned during line drawing
		mapNode.getNodeToDisplay().setOnMouseDragged(null);

		root_pane.setOnKeyPressed(keyEvent-> { //handle escaping from edge creation
			if (drawingEdge != null && keyEvent.getCode() == KeyCode.ESCAPE) {
				if(mapPane.getChildren().contains(drawingEdge.getNodeToDisplay())) //and the right pane has the drawing edge as child
				{
					mapPane.getChildren().remove(drawingEdge.getNodeToDisplay()); //remove from the right pane
				}
				drawingEdge = null;

				mapPane.setOnMouseMoved(null);

				makeMapNodeDraggable(mapNode);
			}
		});

		mapPane.setOnMouseMoved(mouseEvent->{ //handle mouse movement in the right pane

			if (drawingEdge != null)
			{
				//System.out.println("Moving Mouse");
				Point p = MouseInfo.getPointerInfo().getLocation(); // get the absolute current loc of the mouse on screen
				Point2D mouseCoords = drawingEdge.getEdgeLine().screenToLocal(p.x, p.y); // convert coordinates to relative within the window
				drawingEdge.setEndPoint(mouseCoords); //set the end point
			}
		});
	}
	/*
	 removes the node from the model
	 removes the node.getNodeToDisplay() from the map pane
	 removes the node edges associated with that node from the model
	 removes the node edge.getNodeToDisplay() associated with from the map pane
	 */
	private void removeNode(MapNode node)
	{
		for (Iterator<NodeEdge> i = node.getEdges().iterator(); i.hasNext();) {
			NodeEdge edge = (NodeEdge)i.next();
			mapPane.getChildren().remove(edge.getNodeToDisplay()); //remove edge from pane

			model.removeMapEdge(edge); //remove edge from model

			i.remove();
		}

		mapPane.getChildren().remove(node.getNodeToDisplay()); //remove the node

		if(drawingEdge!=null)
		{
			drawingEdge.getNodeToDisplay().setVisible(false); //hide the drawing edge if drawing
			drawingEdge = null; //no longer drawing
		}

		model.removeMapNodeFromCurrentFloor(node); //remove node from mode

		/*********REALLY SHITTY CODEEEE, should specifically use iterator for removal**************/

		TreeItem<MapTreeItem> toDelete = null;

		if(node instanceof Destination)
		{
			for (TreeItem<MapTreeItem> floorItem : getCurrentTreeView().getRoot().getChildren())
			{
				for(TreeItem<MapTreeItem>  nodeItem : floorItem.getChildren())
				{
					if (nodeItem.getValue().getValue().equals((node)))
					{
						toDelete = nodeItem;
						break;
					}
				}
			}

			if(toDelete != null)
			{
				toDelete.getParent().getChildren().remove(toDelete);

				getCurrentTreeView().refresh();
				//treeViewBuilding1.refresh();

				System.out.println("3 strikes and ur out");
			}

			//((Destination)node).getInfo().setName(""); //realllylllylyly hacky
		}
	}

	/*private void setupImportedNode(MapNode droppedNode)
	{
		//droppedNode.setType(droppedNode.getIconType()); //set the type
		mapPane.getChildren().add(droppedNode.getNodeToDisplay()); //add to right panes children
		model.addMapNode(droppedNode); //add node to model

		droppedNode.toFront(); //send the node to the front

						/* Build up event handlers for this droppedNode
		((DragIcon)droppedNode.getNodeToDisplay()).relocateToPoint(new Point2D(droppedNode.getPosX()
				-((DragIcon) droppedNode.getNodeToDisplay()).getWidth()/2,
				droppedNode.getPosY()-((DragIcon) droppedNode.getNodeToDisplay()).getHeight()/2));

		addEventHandlersToNode(droppedNode);

	}*/

	public void updateEdgeWeights(){
		for(NodeEdge e : model.getCurrentFloor().getFloorEdges()){
			e.updateCost();
		}
	}

	/*public void removeHandlers(){
		for(MapNode n : model.getCurrentFloor().getFloorNodes()){
			n.getNodeToDisplay().removeEventHandler(DragEvent.DRAG_DROPPED, onIconDragDropped);
			n.getNodeToDisplay().removeEventHandler(DragEvent.DRAG_OVER, onIconDragOverRightPane);
			n.getNodeToDisplay().removeEventHandler(DragEvent.DRAG_OVER, onIconDragOverRoot);
		}
	}*/

	/**Handles saving out all of the map info
	 * @TODO Save directory changes
	 * @throws IOException
	 */

	@FXML
	public void saveInfoAndExit() throws IOException{
		//removeHandlers();
		updateEdgeWeights();

		refreshNodePositions();

		int i = 0;
		for(NodeEdge e: model.getCurrentFloor().getFloorEdges()){
			System.out.println(Integer.toString(i) + ": Node " + e.getSource().getNodeID() + " Finalized to: " + e.getTarget().getNodeID());
			i++;
		}

		if(model.getCurrentFloor().getKioskNode() == null && model.getCurrentFloor().getFloorNodes().size() > 0){
			System.out.println("ERROR; NO KIOSK NODE SET; SETTING ONE RANDOMLY");
			model.getCurrentFloor().setKioskLocation(model.getCurrentFloor().getFloorNodes().get(0));
		}

		if(DragDropMain.mvm != null) {
			DragDropMain.mvm.setCurrentFloor(this.model.getCurrentFloor());
		}
		else if(Main.mvm != null) {
			Main.mvm.setCurrentFloor(this.model.getCurrentFloor());
		}
		SceneSwitcher.switchToModifyLocationsView(this.getStage());
	}

public void onDirectoryEditorSwitch(ActionEvent actionEvent)
	{
		try
		{
			SceneSwitcher.switchToModifyDirectoryView(this.getStage());
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}

