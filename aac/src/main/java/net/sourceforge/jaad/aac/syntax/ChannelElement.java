package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.sbr.SBR;

/**
 * Created by IntelliJ IDEA.
 * User: stueken
 * Date: 16.04.19
 * Time: 16:55
 */
abstract public class ChannelElement extends Element {

	protected final DecoderConfig config;

	protected ChannelElement(DecoderConfig config) {
		this.config = config;
	}

	abstract void decode(BitStream in, DecoderConfig conf);

	/**
	 * @return if this element represents a channel pair.
	 */
	abstract public boolean isChannelPair();

	/**
	 * A single channel may produce stereo using parametric stereo.
	 * @return if this stereo.
	 */
	abstract public boolean isStereo();

	protected SBR sbr;

    int decodeSBR(BitStream in, int count, boolean crc) {

    	if(!config.isSBREnabled()) {
			in.skipBits(count);
			return 0;
		}

   		if(sbr==null) {
			config.setSBRPresent();
   			sbr = new SBR(config.isSmallFrameUsed(), isChannelPair(), config.getOutputFrequency(), config.isSBRDownSampled());
   		}

   		int result = sbr.decode(in, count, crc);
   		if(sbr.isPSUsed())
   			config.setPsPresent();

   		return result;
   	}

   	boolean isSBRPresent() {
   		return sbr!=null;
   	}

   	SBR getSBR() {
   		return sbr;
   	}

    private float[] dataL, dataR;

    private float[] newData() {
    	int len = config.getSampleLength();
    	return new float[len];
	}

    public float[] getDataL() {
    	if(dataL==null)
			dataL = newData();
    	return dataL;
	}

	public float[] getDataR() {
		if(dataR==null)
			dataR = newData();
		return dataR;
    }

	/**
	 * A SCE or LFE may return a second channel if isStereo()
	 * Else the left channel is returned for both.
	 *
	 * @param ch to read.
	 * @return a float array.
	 */
	public float[] getChannelData(int ch) {
		if(ch==0)
			return getDataL();

		if(ch==1) {
			if(isStereo())
				return getDataR();
			else
				return getDataL();
		}

		throw new IndexOutOfBoundsException("invalid channel: " + ch);
	}
}
