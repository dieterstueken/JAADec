/*
 * Copyright (C) 2010 in-somnia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.jaad.aac.sbr2;

import java.util.logging.Level;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.aac.ps.PS;
import net.sourceforge.jaad.aac.syntax.BitStream;
import net.sourceforge.jaad.aac.syntax.Constants;

//TODO: make buffer arrays final with max sizes
public class SBR implements SBRConstants {

	//arguments
	private int sampleFrequency;
	private boolean downSampled;
	private boolean reset;
	//data
	private final SBRHeader header;
	private final ChannelData[] cd;
	private final FrequencyTables tables;
	private boolean coupling;
	//processing buffers
	private final float[][][][] W; //analysis QMF output
	private final float[][][] Xlow, Xhigh;
	private final float[][][][] Y;
	private final float[][][] X;
	//filterbanks
	private final AnalysisFilterbank qmfA;
	private final SynthesisFilterbank qmfS;
	//PS extension
	private PS ps;
	private boolean psUsed;

	public SBR(SampleFrequency sf, boolean downSampled) {
		sampleFrequency = sf.getFrequency()*2;
		this.downSampled = downSampled;

		header = new SBRHeader();
		cd = new ChannelData[2];
		cd[0] = new ChannelData();
		cd[1] = new ChannelData();
		tables = new FrequencyTables();

		W = new float[2][TIME_SLOTS_RATE][TIME_SLOTS_RATE][2]; //for both channels
		Xlow = new float[32][40][2];
		Xhigh = new float[64][40][2];
		Y = new float[2][38+MAX_LTEMP][64][2]; //for both channels
		X = new float[64][32][2];

		qmfA = new AnalysisFilterbank();
		qmfS = new SynthesisFilterbank();

		psUsed = false;
	}

	/*========================= decoding =========================*/
	public void decode(BitStream in, int count, boolean stereo, boolean crc) throws AACException {
		final int pos = in.getPosition();

		if(crc) {
			Constants.LOGGER.info("SBR CRC bits present");
			in.skipBits(10); //TODO: implement crc check
		}

		if(in.readBool()) {
			header.decode(in);
			tables.calculate(header, sampleFrequency);
		}

		//if at least one header was present yet: decode, else skip
		if(header.isDecoded()) {
			decodeData(in, stereo);

			//check for remaining bits (byte-align) and skip them
			final int len = in.getPosition()-pos;
			final int bitsLeft = count-len;
			if(bitsLeft>=8) Constants.LOGGER.log(Level.WARNING, "SBR: bits left: {0}", bitsLeft);
			else if(bitsLeft<0) throw new AACException("SBR data overread: "+bitsLeft);
			in.skipBits(bitsLeft);
		}
		else {
			final int left = count-pos+in.getPosition();
			in.skipBits(left);
			Constants.LOGGER.log(Level.INFO, "SBR frame without header, skipped {0} bits", left);
		}
	}

	private void decodeData(BitStream in, boolean stereo) throws AACException {
		if(stereo) decodeChannelPairElement(in);
		else decodeSingleChannelElement(in);

		//extended data
		if(in.readBool()) {
			int count = in.readBits(4);
			if(count==15) count += in.readBits(8);
			int bitsLeft = 8*count;

			int extensionID;
			while(bitsLeft>7) {
				bitsLeft -= 2;
				extensionID = in.readBits(2);
				bitsLeft -= decodeExtension(in, extensionID);
			}
			if(bitsLeft>0) in.skipBits(bitsLeft);
		}
	}

	private void decodeSingleChannelElement(BitStream in) throws AACException {
		if(in.readBool()) in.skipBits(4); //reserved

		cd[0].decodeGrid(in, header, tables);
		cd[0].decodeDTDF(in);
		cd[0].decodeInvf(in, header, tables);
		cd[0].decodeEnvelope(in, header, tables, false, false);
		cd[0].decodeNoise(in, header, tables, false, false);
		cd[0].decodeSinusoidal(in, header, tables);

		dequantSingle(0);
	}

	private void decodeChannelPairElement(BitStream in) throws AACException {
		if(in.readBool()) in.skipBits(8); //reserved
		if(coupling = in.readBool()) {
			cd[0].decodeGrid(in, header, tables);
			cd[1].copyGrid(cd[0]);
			cd[0].decodeDTDF(in);
			cd[1].decodeDTDF(in);
			cd[0].decodeInvf(in, header, tables);
			cd[1].copyInvf(cd[0]);
			cd[0].decodeEnvelope(in, header, tables, false, coupling);
			cd[0].decodeNoise(in, header, tables, false, coupling);
			cd[1].decodeEnvelope(in, header, tables, true, coupling);
			cd[1].decodeNoise(in, header, tables, true, coupling);

			dequantCoupled();
		}
		else {
			cd[0].decodeGrid(in, header, tables);
			cd[1].decodeGrid(in, header, tables);
			cd[0].decodeDTDF(in);
			cd[1].decodeDTDF(in);
			cd[0].decodeInvf(in, header, tables);
			cd[1].decodeInvf(in, header, tables);
			cd[0].decodeEnvelope(in, header, tables, false, coupling);
			cd[1].decodeEnvelope(in, header, tables, true, coupling);
			cd[0].decodeNoise(in, header, tables, false, coupling);
			cd[1].decodeNoise(in, header, tables, true, coupling);

			dequantSingle(0);
			dequantSingle(1);
		}

		cd[0].decodeSinusoidal(in, header, tables);
		cd[1].decodeSinusoidal(in, header, tables);
	}

	private int decodeExtension(BitStream in, int extensionID) throws AACException {
		int ret;

		switch(extensionID) {
			case EXTENSION_ID_PS:
				if(ps==null) ps = new PS();
				ret = ps.decode(in);
				if(!psUsed&&ps.hasHeader()) psUsed = true;
				else ret = 0;
				break;
			default:
				in.skipBits(6); //extension data
				ret = 6;
				break;
		}
		return ret;
	}

	/*============ dequantization/stereo decoding (4.6.18.3.5) =============*/
	private void dequantSingle(int ch) {
		//envelopes
		final float a = cd[ch].getAmpRes() ? 1f : 0.5f;
		final float[][] e = cd[ch].getEnvelopeScalefactors();
		final int[] freqRes = cd[ch].getFrequencyResolutions();
		final int[] n = tables.getN();

		for(int l = 0; l<cd[ch].getEnvCount(); l++) {
			for(int k = 0; k<n[freqRes[l]]; k++) {
				e[l][k] = (float) Math.pow(2, e[l][k]*a+6);
			}
		}

		//noise
		final int nq = tables.getNq();
		final float[][] q = cd[ch].getNoiseFloorData();

		for(int l = 0; l<cd[ch].getNoiseCount(); l++) {
			for(int k = 0; k<nq; k++) {
				q[l][k] = (float) Math.pow(2, NOISE_FLOOR_OFFSET-q[l][k]);
			}
		}
	}

	//dequantization of coupled channel pair
	private void dequantCoupled() {
		//envelopes
		final float a = header.getAmpRes() ? 1f : 0.5f;
		final int panOffset = PAN_OFFSETS[cd[0].getAmpRes() ? 1 : 0];
		final float[][] e0 = cd[0].getEnvelopeScalefactors();
		final float[][] e1 = cd[1].getEnvelopeScalefactors();
		final int[] r = cd[0].getFrequencyResolutions();
		final int le = cd[0].getEnvCount();
		final int[] n = tables.getN();

		float d1, d2, d3;
		for(int l = 0; l<le; l++) {
			for(int k = 0; k<n[r[l]]; k++) {
				d1 = (float) Math.pow(2, (e0[l][k]*a)+7);
				d2 = (float) Math.pow(2, (panOffset-e1[l][k])*a);
				d3 = d1/(1+d2);
				e0[l][k] = d3;
				e1[l][k] = d3*d2;
			}
		}

		//noise
		final float[][] q0 = cd[0].getNoiseFloorData();
		final float[][] q1 = cd[1].getNoiseFloorData();
		final int lq = cd[0].getNoiseCount();
		final int nq = tables.getNq();

		for(int l = 0; l<lq; l++) {
			for(int k = 0; k<nq; k++) {
				d1 = (float) Math.pow(2, NOISE_FLOOR_OFFSET-q0[l][k]+1);
				d2 = (float) Math.pow(2, panOffset-q1[l][k]);
				d3 = d1/(1+d2);
				q0[l][k] = d3;
				q0[l][k] = d3*d2;
			}
		}
	}

	/*========================= processing =========================*/
	public boolean isPSUsed() {
		return psUsed;
	}

	//channel: 1024 time samples
	public void processSingleFrame(float[] channel, boolean downSampled) throws AACException {
		processChannel(0, channel, downSampled);
	}

	public void processSingleFramePS(float[] left, float[] right, boolean downSampled) throws AACException {
		processChannel(0, left, downSampled);
		//TODO: PS
	}

	public void processCoupleFrame(float[] left, float[] right, boolean downSampled) throws AACException {
		processChannel(0, left, downSampled);
		processChannel(1, right, downSampled);
	}

	private void processChannel(int ch, float[] data, boolean downSampled) throws AACException {
		//1. old W -> Xlow (4.6.18.5)
		final int kxPrev = tables.getKx(true);
		int l, k;
		//TODO: change all arrays from [k][l] to [l][k]
		for(l = 0; l<T_HF_GEN; l++) {
			for(k = 0; k<kxPrev; k++) {
				Xlow[k][l][0] = W[ch][k][l+TIME_SLOTS_RATE-T_HF_GEN][0];
				Xlow[k][l][1] = W[ch][k][l+TIME_SLOTS_RATE-T_HF_GEN][1];
			}
			for(k = kxPrev; k<32; k++) {
				Xlow[k][l][0] = 0;
				Xlow[k][l][1] = 0;
			}
		}

		//2. analysis QMF (data -> W)
		qmfA.process(data, W[ch], 0);

		//3. new W -> Xlow (4.6.18.5)
		final int kx = tables.getKx(false);
		for(l = T_HF_GEN; l<TIME_SLOTS_RATE+T_HF_GEN; l++) {
			for(k = 0; k<kx; k++) {
				Xlow[k][l][0] = W[ch][l-T_HF_GEN][k][0];
				Xlow[k][l][1] = W[ch][l-T_HF_GEN][k][1];
			}
			for(k = kx; k<32; k++) {
				Xlow[k][l][0] = 0;
				Xlow[k][l][1] = 0;
			}
		}

		//3. HF generation (Xlow -> Xhigh)
		HFGenerator.process(header, tables, cd[ch], Xlow, Xhigh, sampleFrequency);

		//4. old Y -> X
		final int lTemp = cd[ch].getLTemp();
		final int mPrev = tables.getM(true);
		final int m = tables.getM(false);
		for(l = 0; l<lTemp; l++) {
			for(k = 0; k<kxPrev; k++) {
				X[k][l][0] = Xlow[k][l+T_HF_ADJ][0];
				X[k][l][1] = Xlow[k][l+T_HF_ADJ][1];
			}
			for(k = kxPrev; k<kxPrev+mPrev; k++) {
				X[k][l][0] = Y[ch][l+T_HF_ADJ+TIME_SLOTS_RATE][k][0];
				X[k][l][1] = Y[ch][l+T_HF_ADJ+TIME_SLOTS_RATE][k][1];
			}
			for(k = kxPrev+mPrev; k<64; k++) {
				X[k][l][0] = 0;
				X[k][l][1] = 0;
			}
		}

		//5. HF adjustment (Xhigh -> Y)
		HFAdjuster.process(header, tables, cd[ch], Xhigh, Y[ch], reset);

		//6. new Y -> X
		for(l = lTemp; l<TIME_SLOTS_RATE; l++) {
			for(k = 0; k<kx; k++) {
				X[k][l][0] = Xlow[k][l+T_HF_ADJ][0];
				X[k][l][1] = Xlow[k][l+T_HF_ADJ][1];
			}
			for(k = kx; k<kx+m; k++) {
				X[k][l][0] = Y[ch][l+T_HF_ADJ][k][0];
				X[k][l][1] = Y[ch][l+T_HF_ADJ][k][1];
			}
			for(k = kx+m; k<64; k++) {
				X[k][l][0] = 0;
				X[k][l][1] = 0;
			}
		}

		//synthesis (Xlow/Xhigh/Y -> channel); TODO: pass downsampled
		qmfS.process(X, data, ch);

		//save data for next frame
		cd[0].savePreviousData();
		cd[1].savePreviousData();
	}
}