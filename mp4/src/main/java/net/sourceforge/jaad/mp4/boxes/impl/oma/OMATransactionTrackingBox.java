package net.sourceforge.jaad.mp4.boxes.impl.oma;

import net.sourceforge.jaad.mp4.MP4Input;
import net.sourceforge.jaad.mp4.boxes.FullBox;

import java.io.IOException;

/**
 * The OMA DRM Transaction Tracking Box enables transaction tracking as defined 
 * in 'OMA DRM v2.1' section 15.3. The box includes a single transaction-ID and 
 * may appear in both DCF and PDCF.
 * 
 * @author in-somnia
 */
public class OMATransactionTrackingBox extends FullBox {

	private String transactionID;

	public OMATransactionTrackingBox() {
		super("OMA DRM Transaction Tracking Box");
	}

	@Override
	public void decode(MP4Input in) throws IOException {
		super.decode(in);
		transactionID = in.readString(16);
	}

	/**
	 * Returns the transaction-ID of the DCF or PDCF respectively.
	 * 
	 * @return the transaction-ID
	 */
	public String getTransactionID() {
		return transactionID;
	}
}
