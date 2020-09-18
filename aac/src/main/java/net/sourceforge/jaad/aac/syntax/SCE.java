package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.DecoderConfig;

import java.util.List;


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
}
