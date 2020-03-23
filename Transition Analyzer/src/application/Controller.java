package application;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioInputStream;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.Videoio;
import org.opencv.videoio.VideoCapture;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.media.AudioClip;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Text;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.media.Media;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI

	private Mat image;
	private int flag = 0;
	private boolean pl = true;
	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	@FXML
	private Slider slider;
	private MediaPlayer mediaPlayer;
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	
	@FXML
	private void initialize() {
		width = 64;
		height = 64;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 500;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}

	}
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
		// You should modify the logic so that it opens and displays a video
		// Rajan: The following code block was provided by the Oracle Java Docs for JFileChooser with slight modification
	    JFileChooser chooser = new JFileChooser();
	    FileNameExtensionFilter filter = new FileNameExtensionFilter(
	        "mp4, mov, wav", "mp4", "mov", "wav");
	    chooser.setFileFilter(filter);
	    int returnVal = chooser.showOpenDialog(null);
	    if(returnVal == JFileChooser.APPROVE_OPTION) {
	       System.out.println("You chose to open this file: " +
	            chooser.getSelectedFile().getName());
	    }
	    
	    String file = chooser.getSelectedFile().getName(); // name of video
	    String filetype = file.substring(file.lastIndexOf("."),file.length()); // name of extension (.jpg, .mp4, etc.)

	    // Rajan: ALL SELECTED FILES MUST BE IN RESOURCES FOLDER TO WORK!!!
	    // rajan : below code decides whether picture is image or video. issue to fix later: are .gif files images or videos?
			capture = new VideoCapture("resources/" + file); // open video file
			if (capture.isOpened()) { // open successfully
				createFrameGrabber();
			} 
	}
	protected void createFrameGrabber() throws InterruptedException {
		 if (capture != null && capture.isOpened()) { // the video must be open
			 System.out.println("Video open"); // Rajan: Check if the video has been opened
			 double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
			 // create a runnable to fetch new frames periodically
			 Runnable frameGrabber = new Runnable() {
				 @Override
				 public void run() {
					 Mat frame = new Mat();
					 if (capture.read(frame)) { // decode successfully
							 Image im = Utilities.mat2Image(frame);
							 Utilities.onFXThread(imageView.imageProperty(), im);
							 image = frame;
							 double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES); // current frame number
							 double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);   // total frame count
							 slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin())); //this sets slider position
					 } 
					 else { // reach the end of the video
						 capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
					 }
				 }
			 };
			 // terminate the timer if it is running
			 if (timer != null && !timer.isShutdown()) {
				 timer.shutdown();
				 timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			 }
			 // run the frame grabber
			 timer = Executors.newSingleThreadScheduledExecutor();
			 timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
		 }
		}

}
