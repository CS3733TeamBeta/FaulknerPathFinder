package Controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import javax.swing.text.html.ImageView;

public class editDoctorController {
    @FXML
    Button logout;
    @FXML
    Button backButton;
    @FXML
    Button saveButton;
    @FXML
    ComboBox assignDept; //a list of all the possible depts for the doctor to be assigned to
    //needs listbox choices
    @FXML
    ChoiceBox doctorDept; //a list of all the depts the doctor is currently in
                            //needs listbox choices
    @FXML
    TextField nameFirst;
    @FXML
    TextField nameLast;
    @FXML
    TextField docRoom; //room doctor is located in
    @FXML
    ImageView profImage; //displays doctor's image


}