package pl.instantKarma;


import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.SoftBevelBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author n2god on 11.11.2020
 * @project InstantKarmaApp
 */
public class SoundCaptureAndPlayback extends JPanel implements ActionListener {
    final int bufSize = 16384;

    Capture capture = new Capture();
    Playback playback = new Playback();
    AudioInputStream audioInputStream;
    JButton playButton, captureButton;
    JTextField textField;
    String errStr;
    double duration, seconds;
    File file;

    public SoundCaptureAndPlayback() {
        setLayout(new BorderLayout());
        EmptyBorder eb = new EmptyBorder(5, 5, 5, 5);
        SoftBevelBorder sbb = new SoftBevelBorder(SoftBevelBorder.LOWERED);
        setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel p1 = new JPanel();
        p1.setLayout(new BoxLayout(p1, BoxLayout.X_AXIS));

        JPanel p2 = new JPanel();
        p2.setBorder(sbb);
        p2.setLayout(new BoxLayout(p2, BoxLayout.Y_AXIS));

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBorder(new EmptyBorder(10, 0, 5, 0));
        playButton = addButton("Play", buttonsPanel, false);
        captureButton = addButton("Record", buttonsPanel, true);
        p2.add(buttonsPanel);

        p1.add(p2);
        add(p1);
    }

    public void open() {
    }
    public void close() {
        if (playback.thread != null) {
            playButton.doClick(0);
        }
        if (capture.thread != null) {
            captureButton.doClick(0);
        }
    }
    private JButton addButton(String name, JPanel p, boolean state) {
        JButton b = new JButton(name);
        b.addActionListener(this);
        b.setEnabled(state);
        p.add(b);
        return b;
    }
    public void actionPerformed(ActionEvent e) {
        Object obj = e.getSource();
        if (obj.equals(playButton)) {
            if (playButton.getText().startsWith("Play")) {
                playback.start();
                captureButton.setEnabled(false);
                playButton.setText("Stop");
            } else {
                playback.stop();
                captureButton.setEnabled(true);
                playButton.setText("Play");
            }
        } else if (obj.equals(captureButton)) {
            if (captureButton.getText().startsWith("Record")) {
                capture.start();
                playButton.setEnabled(false);
                captureButton.setText("Stop");
            } else {
                capture.stop();
                playButton.setEnabled(true);
            }

        }
    }

    public class Playback implements Runnable {

        SourceDataLine line;
        Thread thread;

        public void start() {
            errStr = null;
            thread = new Thread(this);
            thread.setName("Playback");
            thread.start();
        }
        public void stop() {
            thread = null;
        }
        private void shutDown(String message) {
            if ((errStr = message) != null) {
                System.err.println(errStr);
            }
            if (thread != null) {
                thread = null;
                captureButton.setEnabled(true);
                playButton.setText("Play");
            }
        }

        @Override
        public void run() {
            if (audioInputStream == null) {
                shutDown("No loaded audio to play back");
                return;
            }

            try {
                audioInputStream.reset();
            } catch (Exception e) {
                shutDown("Unable to reset the stream\n" + e);
                return;
            }

            AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
            float rate = 44100.0f;
            int channels = 2;
            int frameSize = 4;
            int sampleSize = 16;
            boolean bigEndian = true;

            AudioFormat format = new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize / 8)
                    * channels, rate, bigEndian);

            AudioInputStream playbackInputStream = AudioSystem.getAudioInputStream(format,
                    audioInputStream);

            if (playbackInputStream == null) {
                shutDown("Unable to convert stream of format " + audioInputStream + " to format " + format);
                return;
            }

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                shutDown("Line matching " + info + " not supported.");
                return;
            }

            try {
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format, bufSize);
            } catch (LineUnavailableException ex) {
                shutDown("Unable to open the line: " + ex);
                return;
            }

            int frameSizeInBytes = format.getFrameSize();
            int bufferLengthInFrames = line.getBufferSize() / 8;
            int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
            byte[] data = new byte[bufferLengthInBytes];
            int numBytesRead = 0;

            line.start();

            while (thread != null) {
                try {
                    if ((numBytesRead = playbackInputStream.read(data)) == -1) {
                        break;
                    }
                    int numBytesRemaining = numBytesRead;
                    while (numBytesRemaining > 0) {
                        numBytesRemaining -= line.write(data, 0, numBytesRemaining);
                    }
                } catch (Exception e) {
                    shutDown("Error during playback: " + e);
                    break;
                }
            }

            if (thread != null) {
                line.drain();
            }
            line.stop();
            line.close();
            line = null;
            shutDown(null);
        }
    }

    class Capture implements Runnable {

        TargetDataLine line;
        Thread thread;

        public void start() {
            errStr = null;
            thread = new Thread(this);
            thread.setName("Capture");
            thread.start();
        }
        public void stop() {
            thread = null;
        }
        private void shutDown(String message) {
            if ((errStr = message) != null && thread != null) {
                thread = null;
                playButton.setEnabled(true);
                captureButton.setText("Record");
                System.err.println(errStr);
            }
        }

        @Override
        public void run() {
            duration = 0;
            audioInputStream = null;

            //define required attributes to line and make sure the line is supported
            AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
            float frameRate = 44100.0f;
            int channels = 2;
            int sampleSizeInBits = 16;
            int frameSize = (sampleSizeInBits / 8) * channels;
            boolean bigEndian = true;

            AudioFormat format = new AudioFormat(encoding, frameRate, sampleSizeInBits,
            channels, frameSize, frameRate, bigEndian);

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if(!AudioSystem.isLineSupported(info)){
                shutDown("Line" + info  + "not supported");
                return;
            }

            //get and open line to record

            try{
                line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format, line.getBufferSize());
                if(line.isControlSupported(FloatControl.Type.VOLUME)){
                    FloatControl control = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                    System.out.println("Volume:"+control.getValue());
                    JProgressBar pb = new JProgressBar();
                    // if you want to set the value for the volume 0.5 will be 50%
                    // 0.0 being 0%
                    // 1.0 being 100%
                    //control.setValue((float) 0.5);
                    int value = (int) (control.getValue()*100);
                    pb.setValue(value);
                    JLabel j = new JLabel(info.toString());
                    j.add(pb);
                } else{
                    System.out.println("Line" + line + " is not supported.");
                }
            } catch (LineUnavailableException e) {
                shutDown("Unable to open the line " + e);
                return;
            } catch (SecurityException e){
                shutDown(e.toString());
                return;
            } catch (Exception e){
                shutDown(e.toString());
                return;
            }
            // play back the captured audio data
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int frameSizeInBytes = format.getFrameSize();
            int bufferLengthInFrames = line.getBufferSize() / 8;
            int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
            byte[] data = new byte[bufferLengthInBytes];
            int numBytesRead;

            line.start();

            while (thread != null) {
                if ((numBytesRead = line.read(data, 0, bufferLengthInBytes)) == -1) {
                    break;
                }
                out.write(data, 0, numBytesRead);
            }

            // we reached the end of the stream.
            // stop and close the line.
            line.stop();
            line.close();
            line = null;

            // stop and close the output stream
            try {
                out.flush();
                out.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            // load bytes into the audio input stream for playback

            byte audioBytes[] = out.toByteArray();
            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            audioInputStream = new AudioInputStream(bais, format, audioBytes.length / frameSizeInBytes);

            long milliseconds = (long) ((audioInputStream.getFrameLength() * 1000) / format
                    .getFrameRate());
            duration = milliseconds / 1000.0;

            try {
                audioInputStream.reset();
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
        }
    }
}
