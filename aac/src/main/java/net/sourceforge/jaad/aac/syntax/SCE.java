package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.Profile;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.sourceforge.jaad.aac.tools.LTPrediction;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * single_channel_element: abbreviaton SCE.
 *
 * Syntactic element of the bitstream containing coded
 * data for a single audio channel. A single_channel_element basically
 * consists of an individual_channel_stream. There may be up to 16
 * such elements per raw data block, each one must have a unique
 * element_instance_tag.
 */

class SCE extends ChannelElement {

	static final Logger LOGGER = Logger.getLogger("jaad.SCE"); //for debugging

	public static final Type TYPE = Type.SCE;

    static class Tag extends ChannelTag {

		protected Tag(int id) {
			super(id);
		}

		@Override
		public boolean isChannelPair() {
			return false;
		}

		@Override
		public Type getType() {
			return TYPE;
		}


		@Override
		public ChannelElement newElement(DecoderConfig config) {
			return new SCE(config, this);
		}
	}

	public static final List<Tag> TAGS = Element.createTagList(16, Tag::new);

    private final ICStream ics;

	SCE(DecoderConfig config, Tag tag) {
		super(config, tag);
		ics = new ICStream(config);
	}

	public void decode(BitStream in) {
		ics.decode(in, false, config);
	}

	public ICStream getICStream() {
		return ics;
	}

	@Override
	public boolean isChannelPair() {
		return false;
	}

	@Override
	public boolean isStereo() {
		if(sbr!=null&&config.isSBREnabled()) {
			if(sbr.isPSUsed())
				return true;
		}

		return false;
	}

	public List<float[]> process(FilterBank filterBank, List<CCE> cces) {
		final ICSInfo info = ics.getInfo();

		//inverse quantization
		final float[] iqData = ics.getInvQuantData();

		final float[] dataL = getDataL();

		final SampleFrequency sf = config.getSampleFrequency().getNominal();

		//prediction
		if(config.getProfile().equals(Profile.AAC_MAIN)&&info.isICPredictionPresent())
			info.getICPrediction().process(ics, iqData, sf);

		final LTPrediction ltp = info.getLTPrediction();
		if(ltp!=null)
			ltp.process(ics, iqData, filterBank, sf);

		//dependent coupling
		processDependentCoupling(cces, CCE.BEFORE_TNS, iqData, null);

		//TNS
		if(ics.isTNSDataPresent())
			ics.getTNS().process(ics, iqData, sf, false);

		//dependent coupling
		processDependentCoupling(cces, CCE.AFTER_TNS, iqData, null);

		//filterbank
		filterBank.process(info.getWindowSequence(), info.getWindowShape(ICSInfo.CURRENT), info.getWindowShape(ICSInfo.PREVIOUS), iqData, dataL, ics.getOverlap());

		if(ltp!=null)
			ltp.updateState(dataL, ics.getOverlap(), config.getProfile());

		//dependent coupling
		processIndependentCoupling(cces, dataL, null);

		//gain control
		if(ics.isGainControlPresent())
			ics.getGainControl().process(iqData, info.getWindowShape(ICSInfo.CURRENT), info.getWindowShape(ICSInfo.PREVIOUS), info.getWindowSequence());


		channelData.clear();
		channelData.add(dataL);

		//SBR
		if(isSBRPresent()&&config.isSBREnabled()) {
			if(dataL.length!=config.getSampleLength())
				LOGGER.log(Level.WARNING, "SBR data present, but buffer has normal size!");

			if(sbr.isPSUsed()) {
				float[] dataR = getDataR();
				getSBR().processPS(dataL, dataR, false);
				channelData.add(dataR);
			}
			else
				getSBR().process(dataL, false);
		}

		return channelData;
	}

}
