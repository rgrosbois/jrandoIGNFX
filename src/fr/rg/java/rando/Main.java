package fr.rg.java.rando;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;

public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("map_ihm.fxml"));
			BorderPane root = (BorderPane) loader.load();
			((IGNMapController)loader.getController()).setMainStage(primaryStage);
			Scene scene = new Scene(root);

			primaryStage.setScene(scene);
			primaryStage.setTitle("Parcours de randonnées");
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
