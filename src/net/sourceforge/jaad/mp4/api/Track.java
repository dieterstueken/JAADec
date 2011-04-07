package net.sourceforge.jaad.mp4.api;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.sourceforge.jaad.mp4.boxes.Box;
import net.sourceforge.jaad.mp4.boxes.BoxTypes;
import net.sourceforge.jaad.mp4.boxes.impl.ChunkOffsetBox;
import net.sourceforge.jaad.mp4.boxes.impl.DataEntryUrlBox;
import net.sourceforge.jaad.mp4.boxes.impl.DataReferenceBox;
import net.sourceforge.jaad.mp4.boxes.impl.MediaHeaderBox;
import net.sourceforge.jaad.mp4.boxes.impl.SampleSizeBox;
import net.sourceforge.jaad.mp4.boxes.impl.SampleToChunkBox;
import net.sourceforge.jaad.mp4.boxes.impl.SampleToChunkBox.SampleToChunkEntry;
import net.sourceforge.jaad.mp4.boxes.impl.TimeToSampleBox;
import net.sourceforge.jaad.mp4.boxes.impl.TrackHeaderBox;

public abstract class Track {

	public static enum Type {

		VIDEO,
		AUDIO
	}
	private final TrackHeaderBox tkhd;
	private final MediaHeaderBox mdhd;
	private final boolean inFile;
	private final List<Frame> frames;
	private URL location;
	private int currentFrame;

	Track(Box box) {
		tkhd = (TrackHeaderBox) box.getChild(BoxTypes.TRACK_HEADER_BOX);

		//mdia
		final Box mdia = box.getChild(BoxTypes.MEDIA_BOX);
		mdhd = (MediaHeaderBox) mdia.getChild(BoxTypes.MEDIA_HEADER_BOX);
		final Box minf = mdia.getChild(BoxTypes.MEDIA_INFORMATION_BOX);

		//dinf
		final Box dinf = minf.getChild(BoxTypes.DATA_INFORMATION_BOX);
		final DataReferenceBox dref = (DataReferenceBox) dinf.getChild(BoxTypes.DATA_REFERENCE_BOX);
		//TODO: support URNs
		if(dref.containsChild(BoxTypes.DATA_ENTRY_URL_BOX)) {
			DataEntryUrlBox url = (DataEntryUrlBox) dref.getChild(BoxTypes.DATA_ENTRY_URL_BOX);
			inFile = url.isInFile();
			try {
				location = new URL(url.getLocation());
			}
			catch(MalformedURLException e) {
				location = null;
			}
		}
		/*else if(dref.containsChild(BoxTypes.DATA_ENTRY_URN_BOX)) {
		DataEntryUrnBox urn = (DataEntryUrnBox) dref.getChild(BoxTypes.DATA_ENTRY_URN_BOX);
		inFile = urn.isInFile();
		location = urn.getLocation();
		}*/
		else {
			inFile = true;
			location = null;
		}

		//stbl
		final Box stbl = minf.getChild(BoxTypes.SAMPLE_TABLE_BOX);
		final List<Box> children = stbl.getChildren();
		if(children.size()>0) {
			frames = new ArrayList<Frame>();
			parseSampleTable(stbl);
		}
		else frames = Collections.emptyList();
		currentFrame = 0;
	}

	private void parseSampleTable(Box stbl) {
		final double timeScale = mdhd.getTimeScale();

		//get tables from boxes
		final SampleToChunkEntry[] sampleToChunks = ((SampleToChunkBox) stbl.getChild(BoxTypes.SAMPLE_TO_CHUNK_BOX)).getEntries();
		final long[] sampleSizes = ((SampleSizeBox) stbl.getChild(BoxTypes.SAMPLE_SIZE_BOX)).getSampleSizes();
		final ChunkOffsetBox stco;
		if(stbl.containsChild(BoxTypes.CHUNK_OFFSET_BOX)) stco = (ChunkOffsetBox) stbl.getChild(BoxTypes.CHUNK_OFFSET_BOX);
		else stco = (ChunkOffsetBox) stbl.getChild(BoxTypes.CHUNK_LARGE_OFFSET_BOX);
		final long[] chunkOffsets = stco.getChunks();

		final TimeToSampleBox stts = (TimeToSampleBox) stbl.getChild(BoxTypes.TIME_TO_SAMPLE_BOX);
		final long[] sampleCounts = stts.getSampleCounts();
		final long[] sampleDeltas = stts.getSampleDeltas();

		//decode sampleDurations
		final long[] sampleDurations = new long[sampleSizes.length];
		int off = 0;
		for(int i = 0; i<sampleCounts.length; i++) {
			for(int j = 0; j<sampleCounts[i]; j++) {
				sampleDurations[off+j] = sampleDeltas[i];
			}
			off += sampleCounts[i];
		}

		SampleToChunkEntry entry;
		int firstChunk, lastChunk;
		long samples, pos, size;
		double timeStamp;
		int current = 0;

		for(int i = 0; i<sampleToChunks.length; i++) {
			entry = sampleToChunks[i];
			firstChunk = (int) entry.getFirstChunk();
			if(i<sampleToChunks.length-1) lastChunk = (int) sampleToChunks[i+1].getFirstChunk()-1;
			else lastChunk = chunkOffsets.length;

			for(int j = firstChunk; j<=lastChunk; j++) {
				samples = entry.getSamplesPerChunk();
				pos = chunkOffsets[j-1];
				while(samples>0) {
					timeStamp = (sampleDurations[j]*(current-1))/timeScale;
					size = sampleSizes[current-1];
					frames.add(new Frame(getType(), pos, size, timeStamp));

					pos += size;
					current++;
					samples--;
				}
			}
		}

		//sort frames by timestamp
		Collections.sort(frames);
	}

	public abstract Type getType();

	//tkhd
	/**
	 * Returns true if the track is enabled. A disabled track is treated as if
	 * it were not present.
	 * @return true if the track is enabled
	 */
	public boolean isEnabled() {
		return tkhd.isTrackEnabled();
	}

	/**
	 * Returns true if the track is used in the presentation.
	 * @return true if the track is used
	 */
	public boolean isUsed() {
		return tkhd.isTrackInMovie();
	}

	/**
	 * Returns true if the track is used in previews.
	 * @return true if the track is used in previews
	 */
	public boolean isUsedForPreview() {
		return tkhd.isTrackInPreview();
	}

	/**
	 * Returns the time this track was created.
	 * @return the creation time
	 */
	public Date getCreationTime() {
		return Utils.getDate(tkhd.getCreationTime());
	}

	/**
	 * Returns the last time this track was modified.
	 * @return the modification time
	 */
	public Date getModificationTime() {
		return Utils.getDate(tkhd.getModificationTime());
	}

	//mdhd
	/**
	 * Returns the language for this media.
	 * @return the language
	 */
	public Locale getLanguage() {
		return new Locale(mdhd.getLanguage());
	}

	/**
	 * Returns true if the data for this track is present in this file (stream).
	 * If not, <code>getLocation()</code> returns the URL where the data can be
	 * found.
	 * @return true if the data is in this file (stream), false otherwise
	 */
	public boolean isInFile() {
		return inFile;
	}

	/**
	 * If the data for this track is not present in this file (if
	 * <code>isInFile</code> returns false), this method returns the data's
	 * location. Else null is returned.
	 * @return the data's location or null if the data is in this file
	 */
	public URL getLocation() {
		return location;
	}

	//reading
	public Frame getNextFrame() {
		return frames.get(currentFrame++);
	}
}