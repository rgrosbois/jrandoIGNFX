package fr.rg.java.rando;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class DialogParametersController {

	@FXML
	TextField ignKeyTF;

	@FXML
	TextField proxyNameTF;

	@FXML
	TextField proxyPortTF;

	Preferences prefs;
	String cleIGN;
	String proxyHostname;
	String proxyPortNum;

	@FXML
	void initialize() {
		prefs = Preferences.userNodeForPackage(Main.class);
		cleIGN = prefs.get(Main.IGNKEY_KEY, IGNMapController.DEFAULT_IGNKEY);
		ignKeyTF.setText(cleIGN);
		proxyHostname = prefs.get(Main.PROXY_HOSTNAME_KEY, "");
		proxyNameTF.setText(proxyHostname);
		proxyPortNum = prefs.get(Main.PROXY_PORT_NUMBER_KEY, "0");
		proxyPortTF.setText(proxyPortNum);
	}

	@FXML
	void cancelModif(ActionEvent e) {
		((Stage) ignKeyTF.getScene().getWindow()).close();
	}

	@FXML
	void okModif(ActionEvent e) {
		if (cleIGN != null && !cleIGN.equals(ignKeyTF.getText())) {
			prefs.put(Main.IGNKEY_KEY, ignKeyTF.getText());
		}
		if (proxyHostname != null && !proxyHostname.equals(proxyNameTF.getText())) {
			prefs.put(Main.PROXY_HOSTNAME_KEY, proxyNameTF.getText());
		}
		if (proxyPortNum != null && !proxyPortNum.equals(proxyPortTF.getText())) {
			prefs.put(Main.PROXY_PORT_NUMBER_KEY, proxyPortTF.getText());
		}
		try {
			prefs.flush();
		} catch (BackingStoreException e1) {
			e1.printStackTrace();
		}
		((Stage) ignKeyTF.getScene().getWindow()).close();
	}
}
