package Controller.User;

import Domain.Navigation.DirectionFloorStep;
import Domain.Navigation.DirectionStep;
import Domain.Navigation.Guidance;
import Domain.ViewElements.Events.StepChangedEvent;
import Domain.ViewElements.Events.StepChangedEventHandler;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.PopOver;

import java.io.IOException;
import java.util.ArrayList;

public class UserDirectionsPanel extends AnchorPane
{

    Guidance guidance;
    int stepIndex = 0;
    ImageView MapImage;
    ArrayList<StepChangedEventHandler> stepChangedEventHandlers;

    UserMapViewController cont;

    public UserDirectionsPanel(ImageView mapImage, UserMapViewController cont)
    {
        this.cont = cont;

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource(
                "../../User/UserDirectionsPanel.fxml"));

        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        stepChangedEventHandlers = new ArrayList<>();

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        this.MapImage = mapImage;
        System.out.println("1");
    }

    @FXML
    public AnchorPane mainPane;

    @FXML
    private GridPane locationGridPane;

    @FXML
    private JFXListView<Label> directionsListView;

    @FXML
    private ImageView previousButton;

    @FXML
    private ImageView nextButton;

    @FXML
    private Label previousLabel;

    @FXML
    private JFXTextField emailField;

    @FXML
    private JFXButton sendEmailButton;

    @FXML
    private ImageView closeButton;

    public void setCloseHandler(EventHandler<? super MouseEvent> e)
    {
        closeButton.setOnMouseClicked(e);
    }

    public void setGuidance(Guidance g) {
        stepIndex = 0;
        guidance = g; //@TODO Make GUIDANCE ITERABLE

    }

    public void addOnStepChangedHandler(StepChangedEventHandler h) {

        stepChangedEventHandlers.add(h);

    }

    public void onStepChanged(DirectionFloorStep step) {
        for(StepChangedEventHandler stepChangedEventHandler: stepChangedEventHandlers) {
            stepChangedEventHandler.handle(new StepChangedEvent(step));
        }
    }

    public void displaySelected(DirectionStep step) {
        System.out.println("print it");
        System.out.println("directionStep has string " + step.toString() + " and " + step.getStepEdges().size() + " edges.");
        cont.highlightStep(step);

    }

    public void fillGuidance(Guidance g)
    {
        this.guidance = g;
        stepIndex = 0;

        fillDirectionsList(stepIndex);

    }

    public void fillDirectionsList(int index) {
        fillDirectionsList(guidance.getSteps().get(index));
    }

    public void fillDirectionsList(DirectionFloorStep step) {

        directionsListView.getItems().clear();

        for(DirectionStep aDirectionStep: step.getDirectionSteps()) {
            Label l = new Label(aDirectionStep.getInstruction());
            l.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    displaySelected(aDirectionStep);
                }
            });
            directionsListView.getItems().add(l);
        }
    }

    @FXML
    void onCloseButtonClicked(MouseEvent event) {

    }

    @FXML
    void onNextButtonClicked(MouseEvent event) {

        if(!(stepIndex < guidance.getSteps().size()-2)) {
            nextButton.setOpacity(0.0);
        } else if(stepIndex < guidance.getSteps().size()-2) {
            previousButton.setOpacity(1.0);
            stepIndex++;
            fillDirectionsList(guidance.getSteps().get(stepIndex));
            onStepChanged(guidance.getSteps().get(stepIndex));
            if(!(stepIndex < guidance.getSteps().size()-2)) {
                nextButton.setOpacity(0.0);
            }
        } else {
            System.out.println("NO! Bad dog!");
        }
    }


    @FXML
    void onPreviousButtonClicked(MouseEvent event) {

        if(!(stepIndex >= 1)) {
            previousButton.setOpacity(0.0);
        } else if(stepIndex >= 1) {
            nextButton.setOpacity(1.0);
            stepIndex--;
            fillDirectionsList(guidance.getSteps().get(stepIndex));
            onStepChanged(guidance.getSteps().get(stepIndex));
            if(!(stepIndex >= 1)) {
                previousButton.setOpacity(0.0);
            }
        } else {
            System.out.println("NO! Bad dog!");
        }
    }

    @FXML
    void onSendEmail(ActionEvent event) {
        Runnable sendEmail = () -> {
            if(guidance!=null)
            {
                guidance.sendEmailGuidance(emailField.getText());
            }
        };

        new Thread(sendEmail).start();
    }
}