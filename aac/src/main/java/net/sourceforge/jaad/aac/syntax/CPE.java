package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.tools.MSMask;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * channel_pair_element: abbreviation CPE.
 *
 * Syntactic element of the bitstream payload containing data for a pair of channels.
 * A channel_pair_element consists of two individual_channel_streams and additional
 * joint channel coding information. The two channels may share common side information.
 *
 * The channel_pair_element has the same restrictions as the single channel element
 * as far as element_instance_tag, and number of occurrences.
 */

public class CPE extends ChannelElement {
	static final Logger LOGGER = Logger.getLogger("jaad.aac.syntax.CPE"); //for debugging

	private MSMask msMask;
	private boolean[] msUsed;
	private boolean commonWindow;
	private final ICStream icsL, icsR;

	CPE(DecoderConfig config) {
		super(config);
		msUsed = new boolean[MAX_MS_MASK];
		icsL = new ICStream(config);
		icsR = new ICStream(config);
	}

	public boolean isChannelPair() {
 		return true;
	}

	public boolean isStereo() {
 		return true;
	}

	void decode(BitStream in, DecoderConfig conf) {
		readElementInstanceTag(in);

		commonWindow = in.readBool();
		final ICSInfo infoL = icsL.getInfo();
		final ICSInfo infoR = icsR.getInfo();

		LOGGER.log(Level.FINE, ()->String.format("CPE %s", commonWindow? "common":""));

		if(commonWindow) {
			infoL.decode(in, conf, commonWindow);
			infoR.setCommonData(in, conf, infoL);

			msMask = MSMask.forInt(in.readBits(2));
			if(msMask.equals(MSMask.TYPE_USED)) {
				final int maxSFB = infoL.getMaxSFB();
				final int windowGroupCount = infoL.getWindowGroupCount();

				for(int idx = 0; idx<windowGroupCount*maxSFB; idx++) {
					msUsed[idx] = in.readBool();
				}
			}
			else if(msMask.equals(MSMask.TYPE_ALL_1))
				Arrays.fill(msUsed, true);

			else if(msMask.equals(MSMask.TYPE_ALL_0))
				Arrays.fill(msUsed, false);

			else
				throw new AACException("reserved MS mask type used");
		}
		else {
			msMask = MSMask.TYPE_ALL_0;
			Arrays.fill(msUsed, false);
		}

		icsL.decode(in, commonWindow, conf);
		icsR.decode(in, commonWindow, conf);
	}

	public ICStream getLeftChannel() {
		return icsL;
	}

	public ICStream getRightChannel() {
		return icsR;
	}

	public MSMask getMSMask() {
		return msMask;
	}

	public boolean isMSUsed(int off) {
		return msUsed[off];
	}

	public boolean isMSMaskPresent() {
		return !msMask.equals(MSMask.TYPE_ALL_0);
	}

	public boolean isCommonWindow() {
		return commonWindow;
	}
}
