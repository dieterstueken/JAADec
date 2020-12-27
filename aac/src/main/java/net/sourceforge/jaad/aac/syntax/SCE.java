package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.sourceforge.jaad.aac.sbr.SBR;

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
		super.decode(in);
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

		//inverse quantization
		final float[] iqData = ics.getInvQuantData();
		final float[] dataL = getDataL();

		//prediction
		ics.processICP();

		ics.processLTP(filterBank);

		//dependent coupling
		processDependentCoupling(cces, CCE.BEFORE_TNS, iqData, null);

		//TNS
		ics.processTNS();

		//dependent coupling
		processDependentCoupling(cces, CCE.AFTER_TNS, iqData, null);

		//filterbank
		ics.process(dataL, filterBank);

		ics.updateLTP(dataL);

		//dependent coupling
		processIndependentCoupling(cces, dataL, null);

		//gain control
		ics.processGainControl();

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
			else {
				getSBR().process(dataL, false);
				channelData.add(dataL);
			}
		} else if(dataL.length!=config.getFrameLength()) {
			SBR.upsample(dataL);
		}

		return channelData;
	}

}
