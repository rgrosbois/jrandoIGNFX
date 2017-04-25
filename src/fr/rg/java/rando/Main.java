package fr.rg.java.rando;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;

public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Map_ihm.fxml"));
			BorderPane root = (BorderPane) loader.load();
			IGNMapController controleur = ((IGNMapController) loader.getController());
			controleur.setMainStage(primaryStage);
			Scene scene = new Scene(root);

			primaryStage.setScene(scene);
			primaryStage.setTitle("Parcours de randonnées");
			primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {

				@Override
				public void handle(WindowEvent event) {
					controleur.closeConnections();
					System.exit(0);
				}
			});
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
