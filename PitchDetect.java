/**
 * This is a robust polyphonic multi-pitch detector.
 * The algorithm is described by Anssi Klapuri in
 * Multiple Fundamental Frequency Estimation by Summing Harmonic Amplitudes.
 * Yuan Gao, 2011
 */
import processing.core.*;
import processing.xml.*;

import ddf.minim.*;
import ddf.minim.analysis.*;

public class PitchDetect extends PApplet
{
    /** size of the buffer */
    private int timeSize;

    /** sample rate of the samples in the buffer */
    private float sampleRate;

    /** FFT object for Fast-Fourier Transform */
    private FFT fft;

    /** spectrum "whitener" for pre-processing */
    private SpecWhitener sw;

    /** spectrum to be analyzed */
    private float[] spec;

    /** array to hold fzeros info, 1 := positive, 0 := negative */
    public int[] fzeros;

    public final float[] PITCHES = { 41.2f, 43.7f, 46.2f, 49.0f, 51.9f, 55.0f, 58.3f, 61.7f, 65.4f, 69.3f, 
                                    73.4f, 77.8f, 82.4f, 87.3f, 92.5f, 98.0f, 103.8f, 110.0f, 116.5f, 123.5f, 
                                    130.8f, 138.6f, 146.8f, 155.6f, 164.8f, 174.6f, 185.0f, 196.0f, 207.7f, 220.0f, 
                                    233.1f, 246.9f, 261.6f, 277.2f, 293.7f, 311.1f, 329.6f, 349.2f, 370.0f, 392.0f, 
                                    415.3f, 440.0f, 466.2f, 493.9f, 523.3f, 554.4f, 587.3f, 622.3f, 659.3f, 698.5f, 
                                    740.0f, 784.0f, 830.6f, 880.0f, 932.3f, 987.8f, 1046.5f, 1108.7f, 1174.7f, 1244.5f, 
                                    1318.5f, 1396.9f, 1480.0f, 1568.0f, 1661.2f, 1760.0f, 1864.7f, 1979.5f, 2093.0f }; // 69 tones

    public PitchDetect(int timeSize, float sampleRate)
    {
        this.timeSize = timeSize;
        this.sampleRate = sampleRate;
        fft = new FFT(timeSize, sampleRate);
        fft.window(FFT.HAMMING);
        sw = new SpecWhitener(timeSize, sampleRate);
        spec = new float[timeSize/2+1];
        fzeros = new int[PITCHES.length];
    }
  
    /**
     *  This method takes an AudioBuffer object as argument.
     *  It detects all notes in presence in buffer.
     */
    public void detect(AudioBuffer buffer)
    {
        // perform fft on the buffer
        fft.forward(buffer);
        for (int i = 0; i < spec.length; i++) spec[i] = 1000 * fft.getBand(i);

        // spectrum pre-processing
        sw.whiten(spec);
        spec = sw.wSpec;

        // iteratively find all presented pitches
        float test = 0, lasttest = 0;
        int loopcount = 1;
        float[] fzeroInfo = new float[3]; // ind 0 is the pitch, ind 1 its salience, ind 2 its ind in PITCHES
        while (true) {
            detectfzero(spec, fzeroInfo);
            lasttest = test;
            test = (test + fzeroInfo[1]) / pow(loopcount, .7f);
            if (test <= lasttest) break;
            loopcount++;

            // subtract the information of the found pitch from the current spectrum
            for (int i = 1; i * fzeroInfo[0] < sampleRate / 2; ++i) {
                int partialInd = floor(i * fzeroInfo[0] * timeSize / sampleRate);
                float weighting = (fzeroInfo[0] + 52) / (i * fzeroInfo[0] + 320);
                spec[partialInd] *= (1 - 0.89f * weighting);
                spec[partialInd-1] *= (1 - 0.89f * weighting);
            }

            // update fzeros
            if (fzeros[(int) fzeroInfo[2]] == 0) fzeros[(int) fzeroInfo[2]] = 1;
        }
    }
  
    // utility function for detecting a single pitch
    private void detectfzero(float[] spec, float[] fzeroInfo)
    {
        float maxSalience = 0;
        for (int j = 0; j < PITCHES.length; ++j) {
            float cSalience = 0; // salience of the candidate pitch
            float val = 0;
            for (int i = 1; i * PITCHES[j] < sampleRate / 2; ++i) {
                int bin = round(i * PITCHES[j] * timeSize / sampleRate);
                // use the largest value of bins in vicinity
                if (bin == timeSize/2) val = max(spec[bin-3], spec[bin-2], spec[bin-1]);
                else if (bin == timeSize/2-1) val = max(max(spec[bin-3], spec[bin-2], spec[bin-1]), spec[bin]);
                else val = max(max(spec[bin-3], spec[bin-2], spec[bin-1]), spec[bin], spec[bin+1]);
                // calculate the salience of the current candidate
                float weighting = (PITCHES[j] + 52) / (i * PITCHES[j] + 320);
                cSalience += val * weighting;
            }
            if (cSalience > maxSalience) {
                maxSalience = cSalience;
                fzeroInfo[0] = PITCHES[j];
                fzeroInfo[1] = cSalience;
                fzeroInfo[2] = j;
            }
        }
    }
}

final class SpecWhitener extends PApplet
{
    private int bufferSize; // the size of the Fourier Transform
    private float sr; // the sample rate of the samples in the buffer
    private float[] cenFreqs; // center frequencies of bandpass filter banks
    private float[] cenFreqsSteps; // steps of increment
    private int[][] banksRanTable; // each row is a filter; cols are lower band index and upper band index this filter covers
  
    public float[] wSpec; // the whitened specturm
  
    public SpecWhitener(int bufferSize, float sr)
    {
        this.bufferSize = bufferSize;
        this.sr = sr;

        // calculate center frequencies
        cenFreqs = new float[32];
        for (int i = 0; i < cenFreqs.length; ++i) cenFreqs[i] = 229 * (pow(10, (i + 1) / 21.4f) - 1);
        cenFreqsSteps = new float[32];
        for (int i = 1; i < cenFreqsSteps.length; ++i) cenFreqsSteps[i] = cenFreqs[i] - cenFreqs[i - 1];

        // calculate the filter banks range table
        banksRanTable = new int[32][2];
        float bandIndLow = 0, bandIndMid = 0, bandIndUp = 0;
        for (int i = 1; i < cenFreqs.length - 1; ++i) {
            if (i == 1) {
                bandIndLow = (cenFreqs[i - 1] * bufferSize) / sr;
                bandIndMid = (cenFreqs[i] * bufferSize) / sr;
                bandIndUp = (cenFreqs[i + 1] * bufferSize) / sr;
            } else {
                bandIndLow = bandIndMid;
                bandIndMid = bandIndUp;
                bandIndUp = (cenFreqs[i + 1] * bufferSize) / sr;
            }
            banksRanTable[i][0] = ceil(bandIndLow);
            banksRanTable[i][1] = floor(bandIndUp);
        }

        wSpec = new float[bufferSize / 2 + 1];
    }
  
    public void whiten(float[] spec)
    {
        // calculate bandwise compression coefficients
        float[] bwCompCoef = new float[32];
        for (int j = 1; j < bwCompCoef.length - 1; ++j) {
            float sum = 0;
            for (int i = banksRanTable[j][0]; i <= banksRanTable[j][1]; ++i) {
                float bandFreq = i * sr / bufferSize;
                if (bandFreq < cenFreqs[j]) {
                    sum += pow(spec[i], 2) * (bandFreq - cenFreqs[j - 1]) / cenFreqsSteps[j];
                } else {
                    sum += pow(spec[i], 2) * (cenFreqs[j + 1] - bandFreq) / cenFreqsSteps[j + 1];
                }
            }
            bwCompCoef[j] = pow(pow(sum / bufferSize, .5f), .33f-1);
        }

        // calculate steps of increment of bwCompCoef
        float[] bwCompCoefSteps = new float[32];
        for (int i = 1; i < bwCompCoef.length; ++i) bwCompCoefSteps[i] = bwCompCoef[i] - bwCompCoef[i - 1];

        // whitens
        float compCoef = 0;
        int bankCount = 1;
        for (int i = banksRanTable[1][0]; i <= banksRanTable[30][1]; ++i) {
            float bandFreq = i * sr / bufferSize;
            if (bandFreq > cenFreqs[bankCount]) bankCount++;
            if (bwCompCoefSteps[bankCount] > 0) {
                compCoef = (bwCompCoefSteps[bankCount]*(bandFreq-cenFreqs[bankCount-1])/cenFreqsSteps[bankCount])+bwCompCoef[bankCount-1];
            } else {
                compCoef = (-bwCompCoefSteps[bankCount]*(cenFreqs[bankCount]-bandFreq)/cenFreqsSteps[bankCount])+bwCompCoef[bankCount];
            }
            wSpec[i] = spec[i] * compCoef;
        }
    }
}