package pl.instantKarma;

import javax.swing.*;
import java.awt.*;

/**
 * @author n2god on 11.11.2020
 * @project InstantKarmaApp
 */
public class Main {
    public static void main(String[] args) {
        SoundCaptureAndPlayback soundCapture = new SoundCaptureAndPlayback();
        soundCapture.open();
        JFrame f = new JFrame("Capture/Playback");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add("Center", soundCapture);
        f.pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int w = 360;
        int h = 180;
        f.setLocation(screenSize.width / 2, screenSize.height / 2 - h/2);
        f.setSize(w, h);
        f.setVisible(true);
    }
}
