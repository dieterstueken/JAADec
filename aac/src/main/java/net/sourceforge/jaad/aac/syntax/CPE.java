package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.Profile;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.sourceforge.jaad.aac.tools.IS;
import net.sourceforge.jaad.aac.tools.LTPrediction;
import net.sourceforge.jaad.aac.tools.MS;
import net.sourceforge.jaad.aac.tools.MSMask;

import java.util.Arrays;
import java.util.List;
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

	public static final Type TYPE = Type.CPE;

	static class Tag extends ChannelTag {

		protected Tag(int id) {
			super(id);
		}

		@Override
		public boolean isChannelPair() {
			return true;
		}

		@Override
		public Type getType() {
			return TYPE;
		}

		@Override
		public ChannelElement newElement(DecoderConfig config) {
			return new CPE(config, this);
		}
	}

	public static final List<Tag> TAGS = Element.createTagList(16, Tag::new);

	private MSMask msMask;
	private boolean[] msUsed;
	private boolean commonWindow;
	private final ICStream icsL, icsR;

	public CPE(DecoderConfig config, ChannelTag tag) {
		super(config, tag);
		msUsed = new boolean[Constants.MAX_MS_MASK];
		icsL = new ICStream(config);
		icsR = new ICStream(config);
	}

	public boolean isChannelPair() {
 		return true;
	}

	public boolean isStereo() {
 		return true;
	}

	public void decode(BitStream in) {

		commonWindow = in.readBool();
		final ICSInfo infoL = icsL.getInfo();
		final ICSInfo infoR = icsR.getInfo();

		LOGGER.log(Level.FINE, ()->String.format("CPE %s", commonWindow? "common":""));

		if(commonWindow) {
			infoL.decode(in, config, commonWindow);
			infoR.setCommonData(in, config, infoL);

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

		icsL.decode(in, commonWindow, config);
		icsR.decode(in, commonWindow, config);
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

	public List<float[]> process(FilterBank filterBank, List<CCE> cces) {

		final ICSInfo infoL = icsL.getInfo();
		final ICSInfo infoR = icsR.getInfo();

		final float[] dataL = getDataL();
		final float[] dataR = getDataR();

		//inverse quantization
		final float[] iqDataL = icsL.getInvQuantData();
		final float[] iqDataR = icsR.getInvQuantData();

		//MS
		if(isCommonWindow()&isMSMaskPresent())
			MS.process(this, iqDataL, iqDataR);

		SampleFrequency sf = config.getSampleFrequency().getNominal();

		if(config.getProfile().equals(Profile.AAC_MAIN)) {

			if(infoL.isICPredictionPresent()) {
				infoL.getICPrediction().process(icsL, iqDataL, sf);
			}

			if(infoR.isICPredictionPresent()) {
				infoR.getICPrediction().process(icsR, iqDataR, sf);
			}
		}
		//IS
		IS.process(this, iqDataL, iqDataR);

		LTPrediction ltp1 = infoL.getLTPrediction();
		LTPrediction ltp2 = infoR.getLTPrediction();

		if(ltp1!=null) {
			ltp1.process(icsL, iqDataL, filterBank, sf);
		}

		if(ltp2!=null) {
			ltp2.process(icsR, iqDataR, filterBank, sf);
		}

		//dependent coupling
		processDependentCoupling(cces, CCE.BEFORE_TNS, iqDataL, iqDataR);

		//TNS
		if(icsL.isTNSDataPresent())
			icsL.getTNS().process(icsL, iqDataL, sf, false);

		if(icsR.isTNSDataPresent())
			icsR.getTNS().process(icsR, iqDataR, sf, false);

		//dependent coupling
		processDependentCoupling(cces, CCE.AFTER_TNS, iqDataL, iqDataR);

		//filterbank
		filterBank.process(infoL.getWindowSequence(), infoL.getWindowShape(ICSInfo.CURRENT), infoL.getWindowShape(ICSInfo.PREVIOUS), iqDataL, dataL, icsL.getOverlap());
		filterBank.process(infoR.getWindowSequence(), infoR.getWindowShape(ICSInfo.CURRENT), infoR.getWindowShape(ICSInfo.PREVIOUS), iqDataR, dataR, icsR.getOverlap());

		if(ltp1!=null)
			ltp1.updateState(dataL, icsL.getOverlap(), config.getProfile());

		if(ltp2!=null)
			ltp2.updateState(dataR, icsR.getOverlap(), config.getProfile());

		//independent coupling
		processIndependentCoupling(cces, dataL, dataR);

		//gain control
		if(icsL.isGainControlPresent())
			icsL.getGainControl().process(iqDataL, infoL.getWindowShape(ICSInfo.CURRENT), infoL.getWindowShape(ICSInfo.PREVIOUS), infoL.getWindowSequence());

		if(icsR.isGainControlPresent())
			icsR.getGainControl().process(iqDataR, infoR.getWindowShape(ICSInfo.CURRENT), infoR.getWindowShape(ICSInfo.PREVIOUS), infoR.getWindowSequence());

		//SBR
		if(isSBRPresent()&&config.isSBREnabled()) {
			if(dataL.length==config.getFrameLength())
				LOGGER.log(Level.WARNING, "SBR data present, but buffer has normal size!");

			getSBR().process(dataL, dataR, false);
		}

		channelData.clear();
		channelData.add(dataL);
		channelData.add(dataR);

		return channelData;
	}
}
