package Controller.Admin;

import java.net.MalformedURLException;
import java.net.URL;

import Controller.AbstractController;
import Controller.SceneSwitcher;
import Domain.Map.*;
import Domain.ViewElements.DragContainer;
import Domain.ViewElements.DragIcon;
import Domain.ViewElements.DragIconType;
import Domain.ViewElements.Events.EdgeCompleteEvent;
import Domain.ViewElements.Events.EdgeCompleteEventHandler;
import Model.DataSourceClasses.MapTreeItem;
import Model.DataSourceClasses.TreeViewWithItems;
import Model.Database.DatabaseManager;
import Model.MapEditorModel;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.*;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import jfxtras.labs.util.event.MouseControlUtil;
import org.controlsfx.control.PopOver;
import javafx.scene.image.Image;

import static Controller.SceneSwitcher.switchToAddFloor;

import java.awt.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class MapEditorController extends AbstractController {

	private static final double SCALE_DELTA = 1.1;

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

	@FXML
	ScrollPane scroll_pane;

	private DragIcon mDragOverIcon = null;

	private EventHandler<DragEvent> onIconDragOverRoot = null;
	private EventHandler<DragEvent> onIconDragDropped = null;
	private EventHandler<DragEvent> onIconDragOverRightPane = null;
	private MapEditorModel model;
	public static Floor newFloor = null;

	NodeEdge drawingEdge;


	Group mapItems;

	StackPane stackPane;
	public void changeFloorToSaved(String location, Floor floor) throws MalformedURLException {
		//System.out.println(new URL("file:///" + System.getProperty("user.dir") + "/" + location).toString());
		//this.mapImage.setImage(new Image(new URL("file:///" + System.getProperty("user.dir") + "/" + location).toString(), true));
		System.out.println("Here");
		this.mapImage.setImage(floor.getImageInfo().getFXImage());
	}

	public MapEditorController() {

		mapItems = new Group(); 
		
		model = new MapEditorModel();

		//Runs once the edge is drawn from one node to another
		//connects the two, sends sources, positions them etc.
		model.addEdgeCompleteHandler(event->
		{
			System.out.println("Edge Complete Handler Invoked");

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

			mapImage.toBack();
		});

	}

	/**
	 * FXML initialize function
	 */
	@FXML
	private void initialize() 
	{
		mapPane.getChildren().remove(mapImage);
		mapPane.getChildren().add(mapItems);
		mapItems.getChildren().add(mapImage);

		mapItems.relocate(0, 0);


		Group zoomTarget = mapItems;

		Group group = new Group(zoomTarget);

		// stackpane for centering the content, in case the ScrollPane viewport
		// is larget than zoomTarget
		StackPane content = new StackPane(group);
		stackPane = content;

		group.layoutBoundsProperty().addListener((observable, oldBounds, newBounds) -> {
			// keep it at least as large as the content
			content.setMinWidth(newBounds.getWidth());
			content.setMinHeight(newBounds.getHeight());
		});

		scroll_pane.setContent(content);
		content.relocate(0, 0);
		mapPane.relocate(0, 0);

		scroll_pane.setPannable(true);

		scroll_pane.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) -> {
			// use viewport size, if not too small for zoomTarget
			content.setPrefSize(newBounds.getWidth(), newBounds.getHeight());
		});

		content.setOnScroll(evt -> {
				evt.consume();

				final double zoomFactor = evt.getDeltaY() > 0 ? 1.2 : 1 / 1.2;

				Bounds groupBounds = group.getLayoutBounds();
				final Bounds viewportBounds = scroll_pane.getViewportBounds();

				if(groupBounds.getWidth()>800 || evt.getDeltaY()>0) //if max and trying to scroll out
				{
					// calculate pixel offsets from [0, 1] range
					double valX = scroll_pane.getHvalue() * (groupBounds.getWidth() - viewportBounds.getWidth());
					double valY = scroll_pane.getVvalue() * (groupBounds.getHeight() - viewportBounds.getHeight());

					// convert content coordinates to zoomTarget coordinates
					Point2D posInZoomTarget = zoomTarget.parentToLocal(group.parentToLocal(new Point2D(evt.getX(), evt.getY())));

					// calculate adjustment of scroll position (pixels)
					Point2D adjustment = zoomTarget.getLocalToParentTransform().deltaTransform(posInZoomTarget.multiply(zoomFactor - 1));

					// do the resizing
					zoomTarget.setScaleX(zoomFactor * zoomTarget.getScaleX());
					zoomTarget.setScaleY(zoomFactor * zoomTarget.getScaleY());

					// refresh ScrollPane scroll positions & content bounds
					scroll_pane.layout();

					// convert back to [0, 1] range
					// (too large/small values are automatically corrected by ScrollPane)
					groupBounds = group.getLayoutBounds();
					scroll_pane.setHvalue((valX + adjustment.getX()) / (groupBounds.getWidth() - viewportBounds.getWidth()));
					scroll_pane.setVvalue((valY + adjustment.getY()) / (groupBounds.getHeight() - viewportBounds.getHeight()));
				}
		});

		BuildingTabPane.getTabs().clear();

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

		loadBuildingsToTabPane(model.getHospital().getBuildings());

		getCurrentTreeView().getSelectionModel().select(0); //selects first floor

		renderInitialMap();

		mapPane.addEventHandler(MouseEvent.MOUSE_CLICKED, clickEvent -> {
			if(drawingEdge != null)
			{
				Node sourceNode = drawingEdge.getSource().getNodeToDisplay();

				Bounds sourceNodeBounds = sourceNode.getBoundsInParent();

				Point2D clickPoint = new Point2D(clickEvent.getX(), clickEvent.getY());

				if(!sourceNodeBounds.contains(clickPoint))
				{

					MapNode chainLinkNode = DragIcon.constructMapNodeFromType(DragIconType.connector);
					chainLinkNode.setType(DragIconType.connector); //set the type

					// clickPoint = mapPane.localToScreen(clickPoint);
					clickPoint = mapPane.localToScene(clickPoint);

					clickPoint = new Point2D(clickPoint.getX()-12.5, clickPoint.getY()-12.5);

					chainLinkNode.setPosX(clickPoint.getX());
					chainLinkNode.setPosY(clickPoint.getY());

					addToAdminMap(chainLinkNode);

					drawingEdge.setTarget(chainLinkNode);

					onEdgeComplete();
				}
			}
		});

	}

	/**
	 * Takes a collection of buildings and creates tabs for them
	 * @param buildings
	 */
	public void loadBuildingsToTabPane(Collection<Building> buildings) {
		for(Building b : buildings)
		{
			Tab t = makeBuildingTab(b);

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

			if(newFloor != null){
				boolean duplicate = false;
				for(Floor f2 : b.getFloors()) {
					if(f2.getFloorNumber() == newFloor.getFloorNumber()) {
						f2.setImageLocation(newFloor.getImageLocation());
						duplicate = true;
					}
				}
				if(!duplicate) {
					treeView.getRoot().getChildren().add(makeTreeItem(newFloor));
				}
			}

			model.addBuilding(b, t); //adds to building tab map

			treeView.getRoot().getChildren().sort(Comparator.comparing(o -> o.toString()));
		}
	}

	public Tab makeBuildingTab(Building b)
	{
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
					//select floor and change map
				}
			}
		});

		textField.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				label.setText(textField.getText());
				tab.setGraphic(label);
				b.setName(label.getText());
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

		BuildingTabPane.getTabs().add(tab);

		return tab;
	}

	/**
	 * Runs when new building button is clicked
	 *
	 * @param event
	 */
	@FXML
	void onNewBuilding(ActionEvent event)
	{
		Building b = new Building("Building " + (model.getBuildingCount()+1)); //@TODO Hacky fix -BEN

		model.addBuilding(b, makeBuildingTab(b));
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
		try {

			switchToAddFloor(this.getStage());
		} catch (IOException e) {

		}

		treeView.getRoot().getChildren().add(makeTreeItem(f));
	}

	public TreeItem<MapTreeItem> makeTreeItem(Object o)
	{
		MapTreeItem treeObj = new MapTreeItem(o);

		TreeItem<MapTreeItem> treeItem = new TreeItem<>(treeObj);

		treeItem.setExpanded(true);

		return treeItem;
	}


	public void changeFloorSelection(Floor f)
	{
		if(f.getImageLocation() == null){
			try {
				switchToAddFloor(this.getStage());
			}
			catch(IOException e){
				System.out.println("Threw an exception in MapEditorController: changeFloorSelection");
				e.printStackTrace();
			}
		}
		try{
			changeFloorToSaved(f.getImageLocation(), f);
		}catch(MalformedURLException e){
			System.out.println("ERROR IN LOADING FLOORPLAN");
		}
		model.setCurrentFloor(f);
		System.out.println("Changed floor to " + f);

		//change image
		//clear nodes
		//load nodes
	}

	/**
	 * Refreshes the node positions so they're up to date from latest drags/changes
	 *
	 * Probably redundant
	 */
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

	protected void renderInitialMap()
	{
		//and then set all the existing nodes up
		HashSet<NodeEdge> collectedEdges = new HashSet<NodeEdge>();

		for(MapNode n : model.getCurrentFloor().getFloorNodes())
		{
			//System.out.println("Adding node");
			addToAdminMap(n);

			System.out.println("New node has " + n.getEdges());
			
			for(NodeEdge edge: n.getEdges())
			{
				if(!collectedEdges.contains(edge)) collectedEdges.add(edge);
			}
		}

		for(NodeEdge edge : collectedEdges)
		{
		    System.out.println("Loading edge...");

			addHandlersToEdge(edge);
			mapItems.getChildren().add(edge.getNodeToDisplay());

			MapNode source = edge.getSource();
			MapNode target = edge.getTarget();

			//@TODO BUG WITH SOURCE DATA, I SHOULDNT HAVE TO DO THIS

			if(!mapItems.getChildren().contains(source.getNodeToDisplay()))
			{
				addToAdminMap(source);
			}

			if(!mapItems.getChildren().contains(target.getNodeToDisplay()))
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
					mapItems.getChildren().remove(edge.getNodeToDisplay()); //remove from the right pane
					model.removeMapEdge(edge);
				}
			}
		});
	}

	public void onEdgeComplete()
	{
		System.out.println("Edge complete");
		for(EdgeCompleteEventHandler handler : model.getEdgeCompleteHandlers())
		{
			if(!model.getCurrentFloor().getFloorEdges().contains(drawingEdge)){
				model.getCurrentFloor().getFloorEdges().add(drawingEdge);
			}
			handler.handle(new EdgeCompleteEvent(drawingEdge));
		}
	}


	/**
	 * Handler to be called when node is dragged... updates end point of any edges connected to it
	 * Also makes sure the mapnode location gets updated on a drag
	 *
	 * @param n node to keep in sync
	 * @return event handler for mouse event that updates positions of lines when triggered
	 */
	private static void makeMapNodeDraggable (MapNode n) {
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
				stackPane.setOnDragOver(onIconDragOverRightPane);
				stackPane.setOnDragDropped(onIconDragDropped);
				
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

	private void buildDragHandlers() {

		class DragData {

			double startX;
			double startY;
			double startLayoutX;
			double startLayoutY;
			Node dragTarget;
		}

		DragData dragData = new DragData();

		//drag over transition to move widget form left pane to right pane
		onIconDragOverRoot = new EventHandler<DragEvent>()
		{

			@Override
			public void handle(DragEvent event)
			{
				Point2D p = stackPane.sceneToLocal(event.getSceneX(), event.getSceneY());

				//turn on transfer mode and track in the right-pane's context 
				//if (and only if) the mouse cursor falls within the right pane's bounds.
				if (!stackPane.boundsInLocalProperty().get().contains(p))
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

				container.addData("scene_coords", new Point2D(event.getX(), event.getY()));

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
				stackPane.removeEventHandler(DragEvent.DRAG_OVER, onIconDragOverRightPane); //remove the event handlers created on drag start
				stackPane.removeEventHandler(DragEvent.DRAG_DROPPED, onIconDragDropped);
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

						mapItems.getChildren().add(droppedNode.getNodeToDisplay());

						Point2D cursorPoint = container.getValue("scene_coords"); //cursor point

						//scroll_pane.s
						Point p = MouseInfo.getPointerInfo().getLocation(); // get the absolute current loc of the mouse on screen
						Point2D mouseCoords = scroll_pane.screenToLocal(p.x, p.y);

						//@TODO drop in the right place
						droppedNode.setPosX(mouseCoords.getX()+20*Math.pow(mapItems.getScaleX(), 3));          // because mouse drag and pictures should start from upper corner
						droppedNode.setPosY(mouseCoords.getY()+20*Math.pow(mapItems.getScaleY(), 3));

						System.out.println("Scale X: " + scroll_pane.getScaleX());

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
	public void addToAdminMap(MapNode mapNode) {
		addEventHandlersToNode(mapNode);

		if(!mapItems.getChildren().contains(mapNode.getNodeToDisplay()))
		{
			mapItems.getChildren().add(mapNode.getNodeToDisplay()); //add to right panes children
		}

		model.addMapNode(mapNode); //add node to model

		mapNode.toFront(); //send the node to the front
		mapImage.toBack();

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

	/**
	 *
	 * @return Returns the treeview of the currently selected building
	 * @author Benjamin Hylak
	 */
	public TreeViewWithItems<MapTreeItem> getCurrentTreeView() {
		return (TreeViewWithItems<MapTreeItem>)BuildingTabPane.getSelectionModel().getSelectedItem().getContent();
	}

	/**
	 * Adds all of the event handlers to handle dragging, edge creation, deletion etc.
	 * Needs to be called on newly constructed nodes to interact properly with map
	 *
	 * @param mapNode
	 * @author Benjamin Hylak
	 */
	public void addEventHandlersToNode(MapNode mapNode) {
		makeMapNodeDraggable(mapNode); //make it draggable

		/***Handles deletion from a popup menu**/
		mapNode.setOnDeleteRequested(event -> {
			removeNode(event.getSource());
		});

		/**Handles when the node is clicked
		 *
		 * Right Click -> Popover
		 * Double click -> Start Drawing
		 * Single Click + Drawing -> Set location
		 */
		mapNode.getNodeToDisplay().setOnMouseClicked(ev -> {

			if(ev.getButton() == MouseButton.SECONDARY) //if right click
			{
				PopOver popOver = mapNode.getEditPopover();

				/***If the name is set, at it to the tree*/
				popOver.setOnHiding(event -> {
					getCurrentTreeView().refresh(); //refresh the treeview once the popup editor closes
				});

				popOver.show(mapNode.getNodeToDisplay(),
						ev.getScreenX(),
						ev.getScreenY());

			}
			else if (ev.getButton() == MouseButton.PRIMARY) { // deal with other types of mouse clicks
				if(ev.getClickCount() == 2) // double click
				{
					onStartEdgeDrawing(mapNode);
				} //could add code to print location changes here.
			}

			/*** if...
			 * 1. We are drawing
			 * 2. This node was clicked
			 * 3. This node isn't the source of the edge we are drawing
			 */
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

	/**
	 * Runs when a node is double clicked and a line needs to start drawing
	 * @param mapNode
	 */
	public void onStartEdgeDrawing(MapNode mapNode)
	{
		if(drawingEdge != null) //if currently drawing... handles case of right clicking to start a new node
		{
			if(mapItems.getChildren().contains(drawingEdge.getNodeToDisplay())) //and the right pane has the drawing edge as child
			{
				mapItems.getChildren().remove(drawingEdge.getNodeToDisplay()); //remove from the right pane
			}
		}

		drawingEdge = new NodeEdge();
		drawingEdge.setSource(mapNode);

		mapItems.getChildren().add(drawingEdge.getNodeToDisplay());
		drawingEdge.toBack();
		mapImage.toBack();

		mapNode.getNodeToDisplay().setOnMouseDragEntered(null); //sets drag handlers to null so they can't be repositioned during line drawing
		mapNode.getNodeToDisplay().setOnMouseDragged(null);

		root_pane.setOnKeyPressed(keyEvent-> { //handle escaping from edge creation
			if (drawingEdge != null && keyEvent.getCode() == KeyCode.ESCAPE) {
				if(mapItems.getChildren().contains(drawingEdge.getNodeToDisplay())) //and the right pane has the drawing edge as child
				{
					mapItems.getChildren().remove(drawingEdge.getNodeToDisplay()); //remove from the right pane
				}
				drawingEdge = null;

				mapPane.setOnMouseMoved(null);

				makeMapNodeDraggable(mapNode);
			}
		});

		stackPane.setOnMouseMoved(mouseEvent->{ //handle mouse movement in the right pane

			if (drawingEdge != null)
			{
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
			mapItems.getChildren().remove(edge.getNodeToDisplay()); //remove edge from pane

			model.removeMapEdge(edge); //remove edge from model

			i.remove();
		}

		mapItems.getChildren().remove(node.getNodeToDisplay()); //remove the node

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
			}
		}
	}

	public void updateEdgeWeights(){
		for(NodeEdge e : model.getCurrentFloor().getFloorEdges()){
			e.updateCost();
		}
	}

	/**Handles saving out all of the map info
	 * @TODO Save directory changes
	 * @throws IOException
	 */

	@FXML
	public void saveInfoAndExit() throws IOException, SQLException
	{
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

		DatabaseManager.getInstance().saveData();

		SceneSwitcher.switchToUserMapView(this.getStage());
	}

	public void onDirectoryEditorSwitch(ActionEvent actionEvent) throws IOException
	{
		SceneSwitcher.switchToModifyDirectoryView(this.getStage());
	}
}
