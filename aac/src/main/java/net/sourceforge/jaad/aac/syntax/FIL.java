package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.SampleFrequency;

/**
 * fill_element: Abbreviation FIL.
 *
 * Syntactic element that contains fill data.
 *
 * There may be any number of fill elements, that can come
 * in any order in the raw data block.
 */

class FIL extends Element {

	public static class DynamicRangeInfo {

		private static final int MAX_NBR_BANDS = 7;
		private final boolean[] excludeMask;
		private final boolean[] additionalExcludedChannels;
		private boolean pceTagPresent;
		private int pceInstanceTag;
		private int tagReservedBits;
		private boolean excludedChannelsPresent;
		private boolean bandsPresent;
		private int bandsIncrement, interpolationScheme;
		private int[] bandTop;
		private boolean progRefLevelPresent;
		private int progRefLevel, progRefLevelReservedBits;
		private boolean[] dynRngSgn;
		private int[] dynRngCtl;

		public DynamicRangeInfo() {
			excludeMask = new boolean[MAX_NBR_BANDS];
			additionalExcludedChannels = new boolean[MAX_NBR_BANDS];
		}
	}
	private static final int TYPE_FILL = 0;
	private static final int TYPE_FILL_DATA = 1;
	private static final int TYPE_EXT_DATA_ELEMENT = 2;
	private static final int TYPE_DYNAMIC_RANGE = 11;
	private static final int TYPE_SBR_DATA = 13;
	private static final int TYPE_SBR_DATA_CRC = 14;
	private final boolean downSampledSBR;
	private DynamicRangeInfo dri;

	FIL(DecoderConfig config) {
		super();
		this.downSampledSBR = config.isSBRDownSampled();
	}

	@Override
	protected int readElementInstanceTag(BitStream in) {
		super.readElementInstanceTag(in);
		if(elementInstanceTag==15)
			elementInstanceTag += in.readBits(8)-1;

		return elementInstanceTag;
	}

	void decode(BitStream in, ChannelElement prev, SampleFrequency sf, boolean sbrEnabled, boolean smallFrames) {

		// for FIL elements the instance tag is a size instead.
		final int count =  8 * readElementInstanceTag(in);

		final int pos = in.getPosition();
		int left = count;
		while(left>0) {
			left = decodeExtensionPayload(in, left, prev, sf, sbrEnabled, smallFrames);
		}

		final int pos2 = in.getPosition()-pos;
		final int bitsLeft = count-pos2;
		if(bitsLeft>0)
			in.skipBits(pos2);

		else if(bitsLeft<0)
			throw new AACException("FIL element overread: "+bitsLeft);
	}

	private int decodeExtensionPayload(BitStream in, int count, ChannelElement prev, SampleFrequency sf, boolean sbrEnabled, boolean smallFrames) {
		final int type = in.readBits(4);
		int ret = count-4;
		switch(type) {
			case TYPE_DYNAMIC_RANGE:
				ret = decodeDynamicRangeInfo(in, ret);
				break;
			case TYPE_SBR_DATA:
			case TYPE_SBR_DATA_CRC:
				if(sbrEnabled) {
					if(prev instanceof SCE_LFE||prev instanceof CPE) {
						prev.decodeSBR(in, sf, ret, (type==TYPE_SBR_DATA_CRC), downSampledSBR, smallFrames);
						ret = 0;
						break;
					}
					else
						throw new AACException("SBR applied on unexpected element: "+prev);
				}
				else {
					in.skipBits(ret);
					ret = 0;
				}
			case TYPE_FILL:
			case TYPE_FILL_DATA:
			case TYPE_EXT_DATA_ELEMENT:
			default:
				in.skipBits(ret);
				ret = 0;
				break;
		}
		return ret;
	}

	private int decodeDynamicRangeInfo(BitStream in, int count) {
		if(dri==null)
			dri = new DynamicRangeInfo();

		int ret = count;

		int bandCount = 1;

		//pce tag
		if(dri.pceTagPresent = in.readBool()) {
			dri.pceInstanceTag = in.readBits(4);
			dri.tagReservedBits = in.readBits(4);
		}

		//excluded channels
		if(dri.excludedChannelsPresent = in.readBool()) {
			ret -= decodeExcludedChannels(in);
		}

		//bands
		if(dri.bandsPresent = in.readBool()) {
			dri.bandsIncrement = in.readBits(4);
			dri.interpolationScheme = in.readBits(4);
			ret -= 8;
			bandCount += dri.bandsIncrement;
			dri.bandTop = new int[bandCount];
			for(int i = 0; i<bandCount; i++) {
				dri.bandTop[i] = in.readBits(8);
				ret -= 8;
			}
		}

		//prog ref level
		if(dri.progRefLevelPresent = in.readBool()) {
			dri.progRefLevel = in.readBits(7);
			dri.progRefLevelReservedBits = in.readBits(1);
			ret -= 8;
		}

		dri.dynRngSgn = new boolean[bandCount];
		dri.dynRngCtl = new int[bandCount];
		for(int i = 0; i<bandCount; i++) {
			dri.dynRngSgn[i] = in.readBool();
			dri.dynRngCtl[i] = in.readBits(7);
			ret -= 8;
		}
		return ret;
	}

	private int decodeExcludedChannels(BitStream in) {
		int i;
		int exclChs = 0;

		do {
			for(i = 0; i<7; i++) {
				dri.excludeMask[exclChs] = in.readBool();
				exclChs++;
			}
		}
		while(exclChs<57&&in.readBool());

		return (exclChs/7)*8;
	}
}