import processing.core.*;
import processing.xml.*;

import ddf.minim.*;
import ddf.minim.analysis.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class PitchDetectDemo extends PApplet
{
    Minim minim;
    AudioPlayer song;
    PitchDetect pitchDetect;

    int colmax = 1320;
    int rowmax = 600;
    int bufferSize = 4096; // size of buffer for song samples

    int[][] disp = new int[rowmax][88];
    int row;
    int lowEdge;
    boolean paused = false;

    int[] blackPos = { 1, 4, 6, 9, 11, 13, 16, 18, 21, 23, 
                    25, 28, 30, 33, 35, 37, 40, 42, 45, 47, 
                    49, 52, 54, 57, 59, 61, 64, 66, 69, 71, 
                    73, 76, 78, 81, 83, 85 };


    public void setup()
    {
        size(colmax, rowmax+70, P2D);
        textMode(SCREEN);
        textFont(createFont("SanSerif", 12));
        smooth();

        minim = new Minim(this);
        song = minim.loadFile("2.mp3", bufferSize);
        song.loop();

        float framePeriod = bufferSize/song.sampleRate(); // time period represented by the buffer
        frameRate(1/framePeriod); // synchronize frame rate and song buffer
    }

    public void draw()
    {
        if (!paused) {
            // Draw window and keyboard.
            background(0);
            stroke(0);
            fill(255);
            rect(0, rowmax, colmax, 70);
            fill(0);
            for (int i = 1; i < 88; i++) line(15*i, rowmax, 15*i, rowmax+70);
            for (int i = 0; i < blackPos.length; i++) rect(15*blackPos[i], rowmax, 15, rowmax+70);
        
            // create new PitchDetect object and detect all notes in the buffer
            pitchDetect = new PitchDetect(song.bufferSize(), song.sampleRate());
            pitchDetect.detect(song.mix);
            int[] fzeros = pitchDetect.fzeros;

            for (int i = 0; i < fzeros.length; i++)
                if (fzeros[i] == 1) disp[row][i] = 1;
            
            drawPart(disp, lowEdge, rowmax-lowEdge, 0);
            drawPart(disp, 0, lowEdge, rowmax-lowEdge);

            row++; 
            if (row == rowmax) row = 0;
            lowEdge++;
            if (lowEdge == rowmax) lowEdge = 0;
        }
    }

    void drawPart(int[][] disp, int firstRow, int nRows, int yBase)
    {
        stroke(0xFFFF5599);
        for (int i = 0; i < nRows; i++)
            for (int j = 10; j < 88; j++) // lower frequencies tend to be "noisy" (background noise)
                if (disp[i+firstRow][j] == 1)
                    line(15*j, yBase+i, 15*(j+1), yBase+i);
    }

    public void keyPressed()
    {
        // Spacebar toggles play/pause.
        if (key == ' ') {
            paused = !paused;
            if (paused) {
                song.pause();
            } else {
                song.play();
            }
        }
    }

    public void stop()
    {
        song.close();
        minim.stop();
        super.stop();
    }

    static public void main(String args[]) {
        PApplet.main(new String[] { "--bgcolor=#FFFFFF", "PitchDetectDemo" });
    }
}
