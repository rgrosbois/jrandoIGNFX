package fr.rg.java.rando.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javafx.beans.property.SimpleStringProperty;

public class Peer {
	private final SimpleStringProperty ipAddress;
	private long timeStamp; // instant en ms

	public static DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);

	public Peer(String address, long time) {
		ipAddress = new SimpleStringProperty(address);
		timeStamp = time;
	}

	public String getIpAddress() {
		return ipAddress.get();
	}

	public long getTimeStamp() {
		return timeStamp;
	}

	public void setTimeStamp(long time) {
		timeStamp = time;
	}

	public String toString() {
		return ipAddress.get() + df.format(new Date(timeStamp));
	}
}
