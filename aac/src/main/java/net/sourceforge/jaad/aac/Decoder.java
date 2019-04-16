package net.sourceforge.jaad.aac;

import net.sourceforge.jaad.aac.filterbank.FilterBank;
import net.sourceforge.jaad.aac.syntax.BitStream;
import net.sourceforge.jaad.aac.syntax.PCE;
import net.sourceforge.jaad.aac.syntax.SyntacticElements;
import net.sourceforge.jaad.aac.transport.ADIFHeader;

import javax.sound.sampled.AudioFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main AAC decoder class
 * @author in-somnia
 */
public class Decoder {

	static final Logger LOGGER = Logger.getLogger("jaad.aac.Decoder"); //for debugging

	private final DecoderConfig config;
	private final SyntacticElements syntacticElements;
	private final FilterBank filterBank;
	public int frames=0;
	private ADIFHeader adifHeader;

	/**
	 * The methods returns true, if a profile is supported by the decoder.
	 * @param profile an AAC profile
	 * @return true if the specified profile can be decoded
	 * @see Profile#isDecodingSupported()
	 */
	public static boolean canDecode(Profile profile) {
		return profile.isDecodingSupported();
	}

	public static Decoder create(byte[] data) {
		return create(BitStream.open(data));
	}

	public static Decoder create(BitStream in) {
		DecoderConfig config = DecoderConfig.decode(in);
		return create(config);
	}

	public static Decoder create(AudioDecoderInfo info) {
		DecoderConfig config = DecoderConfig.create(info);
		return create(config);
	}

	public static Decoder create(DecoderConfig config) {
		if(config==null)
			throw new IllegalArgumentException("illegal MP4 decoder specific info");
		return new Decoder(config);
	}

	/**
	 * Initializes the decoder with a MP4 decoder specific info.
	 *
	 * After this the MP4 frames can be passed to the
	 * <code>decodeFrame(byte[], SampleBuffer)</code> method to decode them.
	 * 
	 * @param config decoder specific info from an MP4 container
	 * @throws AACException if the specified profile is not supported
	 */
	private Decoder(DecoderConfig config) {
		//config = DecoderConfig.parseMP4DecoderSpecificInfo(decoderSpecificInfo);

		this.config = config;

		syntacticElements = new SyntacticElements(config);
		filterBank = new FilterBank(config.isSmallFrameUsed(), config.getChannelConfiguration().getChannelCount());


		LOGGER.log(Level.INFO, "profile: {0}", config.getProfile());
		LOGGER.log(Level.INFO, "sf: {0}", config.getSampleFrequency().getFrequency());
		LOGGER.log(Level.INFO, "channels: {0}", config.getChannelConfiguration().getDescription());
	}

	public DecoderConfig getConfig() {
		return config;
	}

	/**
	 * Decodes one frame of AAC data in frame mode and returns the raw PCM
	 * data.
	 * @param frame the AAC frame
	 * @param buffer a buffer to hold the decoded PCM data
	 * @throws AACException if decoding fails
	 */
	public void decodeFrame(byte[] frame, SampleBuffer buffer) {

		BitStream in = BitStream.open(frame);

		try {
			LOGGER.log(Level.FINE, ()->String.format("frame %d @%d", frames, 8*frame.length));
			decode(in, buffer);
			LOGGER.log(Level.FINEST, ()->String.format("left %d", in.getBitsLeft()));
		}
		catch(EOSException e) {
			LOGGER.log(Level.WARNING,"unexpected end of frame",e);
		} finally {
			++frames;
		}
	}

	private void decode(BitStream in, SampleBuffer buffer) {
		if(ADIFHeader.isPresent(in)) {
			adifHeader = ADIFHeader.readHeader(in);
			final PCE pce = adifHeader.getFirstPCE();
			config.setProfile(pce.getProfile());
			config.setSampleFrequency(pce.getSampleFrequency());
			config.setChannelConfiguration(ChannelConfiguration.forInt(pce.getChannelCount()));
		}

		if(!canDecode(config.getProfile()))
			throw new AACException("unsupported profile: "+config.getProfile().getDescription());

		syntacticElements.startNewFrame();

		try {
			//1: bitstream parsing and noiseless coding
			syntacticElements.decode(in);
			//2: spectral processing
			syntacticElements.process(filterBank);
			//3: send to output buffer
			syntacticElements.sendToOutput(buffer);
		}
		catch(Exception e) {
			buffer.setData(new byte[0], 0, 0, 0, 0);
			if(e instanceof AACException)
				throw (AACException) e;
			else
				throw new AACException(e);
		}
	}

	public AudioFormat getAudioFormat() {
		int mult = 1;
		final int freq = mult*config.getSampleFrequency().getFrequency();
		return new AudioFormat(freq,16,2, true, false);
	}
}