package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;

/**
 * fill_element: Abbreviation FIL.
 *
 * Syntactic element that contains fill data.
 *
 * There may be any number of fill elements, that can come
 * in any order in the raw data block.
 */

class FIL {

	private static final int TYPE_FILL = 0;
	private static final int TYPE_FILL_DATA = 1;
	private static final int TYPE_EXT_DATA_ELEMENT = 2;
	private static final int TYPE_DYNAMIC_RANGE = 11;
	private static final int TYPE_SBR_DATA = 13;
	private static final int TYPE_SBR_DATA_CRC = 14;

	// decoded but unused.
	private DRC dri;

	void decode(BitStream in, ChannelElement prev) {

		int count = in.readBits(4);
		if(count==15)
			count += in.readBits(8)-1;

		final int pos = in.getPosition();
		final int end = pos + 8 * count;

		int left = end-in.getPosition();

		decodeExtensionPayload(in, left, prev);

		left = end-in.getPosition();

		if(left<0)
			throw new AACException("FIL element overread: "+left);
		else if(left>0)
			in.skipBits(left);
	}

	private void decodeExtensionPayload(BitStream in, int count, ChannelElement prev) {

		int type = TYPE_FILL;
		if(count>=4) {
			type = in.readBits(4);
			count -= 4;
		}

		switch(type) {
			case TYPE_DYNAMIC_RANGE:
				decodeDynamicRangeInfo(in, count);
				break;
			case TYPE_SBR_DATA:
			case TYPE_SBR_DATA_CRC:
				if(prev!=null) {
					count = prev.decodeSBR(in, count, (type == TYPE_SBR_DATA_CRC));
					break;
				}
			case TYPE_FILL:
			case TYPE_FILL_DATA:
			case TYPE_EXT_DATA_ELEMENT:
			default:
				in.skipBits(count);
				break;
		}
	}

	private int decodeDynamicRangeInfo(BitStream in, int count) {
		if (dri == null)
			dri = new DRC();

		return dri.decode(in, count);
	}
}
