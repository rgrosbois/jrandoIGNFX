package fr.rg.java.rando;

import java.awt.MouseInfo;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import fr.rg.java.rando.util.AddressSuggestionService;
import fr.rg.java.rando.util.GeoLocation;
import fr.rg.java.rando.util.KMLReader;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MainController {
	@FXML
	private VBox mainContent;

	// Contrôleur de l'affichage de statistiques
	InfoGraphController infoController;

	// Contrôleur de l'affichage de carte
	IGNMapController mapController;

	/**
	 * Finalise l'initialisation de l'IHM en ajoutant les zones pour la carte et les
	 * statistiques.
	 *
	 * Redimensionne l'application en fonction de la taille de l'écran.
	 */
	@FXML
	void initialize() {
		// Ajouter l'IHM pour la carte
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/IGNMap_ihm.fxml"));
			mainContent.getChildren().add(loader.load());
			mapController = (IGNMapController) loader.getController();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Ajouter l'IHM pour les statistiques
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/InfoGraph_ihm.fxml"));
			mainContent.getChildren().add(loader.load());
			infoController = (InfoGraphController) loader.getController();
			infoController.setMainController(this);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Dimensions du graph et de la carte déduites de celle de l'écran
		Point p = MouseInfo.getPointerInfo().getLocation();
		Screen currentScreen = Screen.getPrimary(); // écran primaire par défaut
		for (Screen s : Screen.getScreens()) { // rechercher souris
			if (s.getBounds().contains(p.x, p.y))
				currentScreen = s;
		}
		// La fenêtre a des dimensions égales à 13/15 de l'écran courant
		Rectangle2D screenBounds = currentScreen.getBounds();
		double prefWidth = 13 * screenBounds.getWidth() / 15;
		double prefHeight = 13 * screenBounds.getHeight() / 15;
		mapController.setPrefWidth(prefWidth);
		infoController.setPrefWidth(prefWidth);

		// Proportions de chaque zone dans la fenêtre
		mapController.setPrefHeight(0.65 * prefHeight);
		infoController.setPrefHeight(0.25 * prefHeight);

		// Géolocalisation initiale pour centrer la carte
		Preferences prefs = Preferences.userNodeForPackage(Main.class);
		GeoLocation centerLoc = new GeoLocation(prefs.getDouble(Main.SAVED_LONGITUDE_KEY, Main.DEFAULT_LONGITUDE),
				prefs.getDouble(Main.SAVED_LATITUDE_KEY, Main.DEFAULT_LATITUDE));
		mapController.initMap(centerLoc);
	}

	/**
	 * Une géolocalisation a été sélectionnée dans le graphe, demander son affichage
	 * sur la carte.
	 *
	 * @param g
	 */
	public void onLocationSelectedOnGraph(GeoLocation g) {
		mapController.highlightLocation(g);
	}

	/**
	 * Charger une trace enregistrée dans un fichier KML.
	 *
	 * @param e
	 */
	@FXML
	void loadTrack(ActionEvent e) {
		// Sélecteur de fichier
		FileChooser chooser = new FileChooser();
		FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("fichier KML (*.kml)", "*.kml");
		chooser.getExtensionFilters().add(filter);

		// Répertoire fichiers KML
		Preferences prefs = Preferences.userNodeForPackage(Main.class);
		chooser.setInitialDirectory(new File(prefs.get(Main.KML_DIR_KEY, "/tmp")));

		File file = chooser.showOpenDialog(mapController.getWindow());
		if (file == null || !file.exists())
			return;

		// Enregistrer le répertoire courant
		prefs.put(Main.KML_DIR_KEY, file.getParent());
		try {
			prefs.flush();
		} catch (BackingStoreException ex) {
			ex.printStackTrace();
		}

		// Extraire les géolocalisations du fichier KML
		HashMap<String, Object> infoKML = new KMLReader().extractLocWithStAXCursor(file.getAbsolutePath());
		ArrayList<GeoLocation> geolist = (ArrayList<GeoLocation>) infoKML.get(KMLReader.LOCATIONS_KEY);

		// Ajouter la trace sur la carte et dans le graphe
		mapController.setTrack(geolist);
		infoController.setTrack(geolist);

		// Mettre à jour le titre
		Stage stage = (Stage) mapController.getWindow();
		stage.setTitle(Main.APP_TITLE + " - " + file.getName());
	}

	@FXML
	void openParameters(ActionEvent e) {
		Dialog<String> dialog = new Dialog<>();
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Dialog_parameters_ihm.fxml"));
			Parent root = loader.load();
			dialog.setTitle("Paramètres");
			dialog.getDialogPane().setContent(root);
			dialog.setGraphic(new ImageView(getClass().getResource("/res/img/Parameters.png").toString()));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		dialog.showAndWait();
	}

	@FXML
	void openAddress(ActionEvent e) {
		ContextMenu listeSuggest = new ContextMenu();
		listeSuggest.hide();

		TextInputDialog dialog = new TextInputDialog();
		dialog.setTitle("Adresse");
		dialog.setHeaderText("Rechercher une adresse");
		dialog.setContentText("adresse:");
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(mapController.getWindow());

		// Autocomplétion
		TextField tf = dialog.getEditor();
		tf.textProperty().addListener(new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				// uniquement si plus de 3 caractères et que la chaîne
				// est tapée à la main (i.e. pas après un clic sur une
				// suggestion)
				if (Math.abs(newValue.length() - oldValue.length()) <= 1 && newValue.length() >= 3) {
					// Configurer une recherche de 5 suggestions
					AddressSuggestionService as = new AddressSuggestionService(newValue, 5);

					// Remplir le menu contextuel avec les suggestions trouvées
					as.setOnSucceeded((WorkerStateEvent event) -> {
						// Récupérer la liste de géolocalisations
						ArrayList<GeoLocation> loc = (ArrayList<GeoLocation>) event.getSource().getValue();

						if (loc.size() > 0) { // Liste contenant au moins 1
												// élément
							// Remplir une liste d'item de menu cliquables
							List<CustomMenuItem> menuItems = new LinkedList<>();
							for (int i = 0; i < loc.size(); i++) {
								String result = loc.get(i).address;
								CustomMenuItem item = new CustomMenuItem(new Label(result), true);
								menuItems.add(item);

								// Si clic sur la suggestion, remplir le
								// TextField et cacher le menu
								item.setOnAction((ActionEvent e1) -> {
									tf.setText(result);
									listeSuggest.hide();
								});
							}

							// Remplacer le contenu du menu avec celui
							// de la nouvelle liste.
							listeSuggest.getItems().clear();
							listeSuggest.getItems().addAll(menuItems);

							// Afficher le menu en dessous du TextField
							listeSuggest.show(tf, Side.BOTTOM, 0, 0);
						} else { // liste vide -> cacher le menu contextuel
							listeSuggest.hide();
						}
					});
					// lancer la recherche
					as.start();
				}
			}
		});

		// Afficher la boîte de dialogue
		Optional<String> result = dialog.showAndWait();

		// Traiter l'éventuelle réponse
		result.ifPresent(address -> {
			// Lancer une nouvelle recherche d'adresse et utiliser la première
			// réponse valide
			AddressSuggestionService as = new AddressSuggestionService(address, 5);
			as.setOnSucceeded((WorkerStateEvent event) -> {
				ArrayList<GeoLocation> loc = (ArrayList<GeoLocation>) event.getSource().getValue();
				mapController.initMap(loc.get(0));
			});
			as.start();
			// Mettre à jour le titre
			Stage stage = (Stage) mapController.getWindow();
			stage.setTitle(Main.APP_TITLE + " - " + address);
		});
	}

}
