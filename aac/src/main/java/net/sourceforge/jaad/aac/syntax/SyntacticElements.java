package net.sourceforge.jaad.aac.syntax;

import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.DecoderConfig;
import net.sourceforge.jaad.aac.filterbank.FilterBank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class SyntacticElements implements Constants {
	static final Logger LOGGER = Logger.getLogger("jaad.SyntacticElements"); //for debugging

	//global properties
	private DecoderConfig config;
	private int bitsRead;

	private final FilterBank filterBank;

	//elements

	private FIL fil;

	private List<CCE> cces = new ArrayList<>();

	private final Map<Element.InstanceTag, Element> elements = new HashMap<>();

	private final List<ChannelElement> audioElements = new ArrayList<>(); //SCE, LFE and CPE

	private List<float[]> channels = new ArrayList<>();

	private Element newElement(Element.InstanceTag tag) {
		return tag.newElement(config);
	}

	private Element getElement(Element.InstanceTag tag) {
		return elements.computeIfAbsent(tag, this::newElement);
	}

	public SyntacticElements(DecoderConfig config) {
		this.config = config;
		filterBank = new FilterBank(config.isSmallFrameUsed(), config.getChannelConfiguration().getChannelCount());

		startNewFrame();
	}

	public final void startNewFrame() {
		bitsRead = 0;
		audioElements.clear();
		cces.clear();
		channels.clear();
	}

	public void decode(BitStream in) {
		final int start = in.getPosition(); //should be 0

		int type;
		if(!config.getProfile().isErrorResilientProfile()) {
			while((type = in.readBits(3))!=ELEMENT_END) {
				switch(type) {
					case ELEMENT_SCE:
						decode(SCE.TAGS, in);
						break;
					case ELEMENT_CPE:
						decode(CPE.TAGS, in);
						break;
					case ELEMENT_CCE:
						decode(CCE.TAGS, in);
						break;
					case ELEMENT_LFE:
						decode(LFE.TAGS, in);
						break;
					case ELEMENT_DSE:
						decode(DSE.TAGS, in);
						break;
					case ELEMENT_PCE:
						decode(PCE.TAGS, in);
						break;
					case ELEMENT_FIL:
						decodeFIL(in);
						break;
				}
			}
		}
		else {
			//error resilient raw data block
			switch(config.getChannelConfiguration()) {
				case CHANNEL_CONFIG_MONO:
					decode(SCE.TAGS, in);
					break;
				case CHANNEL_CONFIG_STEREO:
					decode(CPE.TAGS, in);
					break;
				case CHANNEL_CONFIG_STEREO_PLUS_CENTER:
					decode(SCE.TAGS, in);
					decode(CPE.TAGS, in);
					break;
				case CHANNEL_CONFIG_STEREO_PLUS_CENTER_PLUS_REAR_MONO:
					decode(SCE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(LFE.TAGS, in);
					break;
				case CHANNEL_CONFIG_FIVE:
					decode(SCE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(CPE.TAGS, in);
					break;
				case CHANNEL_CONFIG_FIVE_PLUS_ONE:
					decode(SCE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(LFE.TAGS, in);
					break;
				case CHANNEL_CONFIG_SEVEN_PLUS_ONE:
					decode(SCE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(CPE.TAGS, in);
					decode(LFE.TAGS, in);
					break;
				default:
					throw new AACException("unsupported channel configuration for error resilience: "+config.getChannelConfiguration());
			}
		}
		in.byteAlign();

		bitsRead = in.getPosition()-start;

		LOGGER.finest("END");
	}

	private Element decode(List<? extends Element.InstanceTag> tags, BitStream in) {

		int id = in.readBits(4);
		Element.InstanceTag tag = tags.get(id);

		LOGGER.finest(tag.toString());

		Element element = getElement(tag);

		element.decode(in);

		if(element instanceof ChannelElement) {
			audioElements.add((ChannelElement) element);
		}

		if(element instanceof CCE) {
			cces.add((CCE)element);
		}

		if(element instanceof PCE) {
			PCE pce = (PCE) element;
			config.setAudioDecoderInfo(pce);
		}

		return element;
	}

	private ChannelElement getLastAudioElement() {
		int n = audioElements.size();
		return n==0 ? null : audioElements.get(n-1);
	}

	private void decodeFIL(BitStream in) {

		if(fil==null)
			fil = new FIL();

		fil.decode(in, getLastAudioElement());
	}

	public List<float[]> process() {

		channels.clear();

		for (ChannelElement e : audioElements) {
			channels.addAll(e.process(filterBank, cces));
		}

		return channels;
	}
}
