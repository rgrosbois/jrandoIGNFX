package fr.rg.java.rando;

import java.awt.MouseInfo;
import java.awt.Point;
import java.io.File;
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
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MainController {
	@FXML
	IGNMap map;

	@FXML
	InfoGraph graph;

	@FXML
	void initialize() {
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
		map.setPrefWidth(prefWidth);
		graph.setPrefWidth(prefWidth);
		map.setPrefHeight(0.7 * prefHeight);
		graph.setPrefHeight(0.2 * prefHeight);

		// Géolocalisation initiale pour centrer la carte
		Preferences prefs = Preferences.userNodeForPackage(Main.class);
		GeoLocation centerLoc = new GeoLocation(prefs.getDouble(Main.SAVED_LONGITUDE_KEY, Main.DEFAULT_LONGITUDE),
				prefs.getDouble(Main.SAVED_LATITUDE_KEY, Main.DEFAULT_LATITUDE));
		map.initMap(centerLoc);
	}

	@FXML
	void addTrack(ActionEvent e) {
		// Sélecteur de fichier
		FileChooser chooser = new FileChooser();
		FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("fichier KML (*.kml)", "*.kml");
		chooser.getExtensionFilters().add(filter);

		// Répertoire fichiers KML
		Preferences prefs = Preferences.userNodeForPackage(Main.class);
		chooser.setInitialDirectory(new File(prefs.get(Main.KML_DIR_KEY, "/tmp")));

		File file = chooser.showOpenDialog(map.getScene().getWindow());
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
		ArrayList<GeoLocation> list = (ArrayList<GeoLocation>) infoKML.get(KMLReader.LOCATIONS_KEY);

		// Ajouter la trace sur la carte et dans le graphe
		map.setTrack(list);
		graph.setTrack(list);

		// Mettre à jour le titre
		Stage stage = (Stage)map.getScene().getWindow();
		stage.setTitle(Main.APP_TITLE + " - " + file.getName());
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
		dialog.initOwner(map.getScene().getWindow());

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
				map.initMap(loc.get(0));
			});
			as.start();
			// Mettre à jour le titre
			Stage stage = (Stage)map.getScene().getWindow();
			stage.setTitle(Main.APP_TITLE + " - " + address);
		});
	}

}
