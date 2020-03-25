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
import org.opencv.core.CvType;
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

import java.util.ArrayList;

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
	@FXML
	private Slider speedslider;
	@FXML
	private Slider volslider;
	@FXML 
	private Button pause;
	private MediaPlayer mediaPlayer;
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	
	private int histBin;
	private int totalFrame ; 
	private static int frameNumber;	

	private Mat colSTI; // MAT for copying pixel STI (column)
	private Mat rowSTI; // MAT for copying pixel STI (row)
	private Mat colHistSTI;// MAT for Histogram pixel STI (column)
	private Mat rowHistSTI; // MAT for Histogram pixel STI (row)
	private ArrayList<Mat> frames;//storing all the frames(after chromaticity) for later use (HISTOGRAM)
	private boolean newCP;	

	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
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
	        "jpg, gif, png, mp4, mov, wav, jpeg", "jpg", "gif", "png", "mp4", "mov", "wav", "jpeg", "avi");
	    chooser.setFileFilter(filter);
	    int returnVal = chooser.showOpenDialog(null);
	    if(returnVal == JFileChooser.APPROVE_OPTION) {
	       System.out.println("You chose to open this file: " +
	            chooser.getSelectedFile().getName());
	    }
	    
	    String file = chooser.getSelectedFile().getName(); // rajan: name of video
	    String filetype = file.substring(file.lastIndexOf("."),file.length()); // rajan: name of extension (.jpg, .mp4, etc.)

	    // Rajan: ALL SELECTED FILES MUST BE IN RESOURCES FOLDER TO WORK!!!
	    // rajan : below code decides whether picture is image or video. issue to fix later: are .gif files images or videos?
	    if(filetype.equals(".mp4") || filetype.equals(".mov") || filetype.equals(".wav") || filetype.equals(".gif")) {
			capture = new VideoCapture("resources/" + file); // open video file
			if (capture.isOpened()) { // open successfully
				createFrameGrabber();
			} 
	    }
	    if(filetype.equals(".png") || filetype.equals(".jpg") || filetype.equals(".jpeg")) {
			final String imageFilename = "resources/" + file;
			image = Imgcodecs.imread(imageFilename);
			imageView.setImage(Utilities.mat2Image(image)); 
	    }
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
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
	@FXML
	protected void pause(ActionEvent event) throws InterruptedException{ // pause/play feature
		if(pl) {
		pause.setText("Play");
		}
		if(!pl) {
			pause.setText("Pause");
		}
		pl = !pl;
	}
///////////////////////	
	//set center column, raw; and histogram column, raw
	@FXML 
	protected void centerCOL_STI(ActionEvent event){
		imageView.setImage(Utilities.mat2Image(colSTI));// Suppose to display a center column STI
	}
	@FXML
	protected void centerROW_STI(ActionEvent event){
		imageView.setImage(Utilities.mat2Image(rowSTI)); // Suppose to display a center row STI
	}
	@FXML
	protected void histROW_STI(ActionEvent event) {
		imageView.setImage(Utilities.mat2Image(rowHistSTI)); // Suppose to display a Histogram row STI
	}
	@FXML
	protected void histCOL_STI(ActionEvent event) {
		imageView.setImage(Utilities.mat2Image(colHistSTI)); // Suppose to display a Histogram row STI
	}
	
	protected void copyPixel(ActionEvent event) {
		if (image != null) {
			//filling matrix for center column STI
			Mat col = image.col(Math.round(image.cols()/2)); // this is the center column of this frame

			if(newCP == true) { // if this is the 1st frame coming in , we initialize the MAT
				colSTI = new Mat (image.rows(),totalFrame,CvType.CV_8UC3);
			}
			//puting this center column in to the STI@"frameNumber" column
			col.col(0).copyTo(colSTI.col(frameNumber));
			
			//filling matrix for center row STI
			Mat row = image.row(Math.round(image.rows()/2));// this is the center row of this frame
			if(newCP == true) {// if this is the 1st frame coming in , we initialize the MAT
				rowSTI = new Mat (totalFrame,image.cols(),CvType.CV_8UC3);
			}
			//putting this center row in to the STI@"frameNumber" column
			row.row(0).copyTo(rowSTI.row(frameNumber));
			
			if(newCP == true)
				newCP = false;
			
		}
		else
			System.out.println("No image is selected");
		}	
//Quality of color; (the color plane)
	protected void chromaticity(ActionEvent event) {//create the chromaticity image for fame and call the histogram function		
		if(frameNumber == 0)
			frames = new ArrayList<>();
		if (image != null) {
			Mat chrom = new Mat (image.rows(),image.cols(),CvType.CV_64FC2); // Mat object that stores the chromaticity values for this frame 
			//convert the mat from CV_8UC3/CV_8UC2 to CV_64FC3/CV_64FC2 
			//CV_xxTCn : xx is the number of bit, 
			//T is the type (F-float , S-signedInterger, U-unsignedInterger)
			//C means channel and n is the number of channels.
			image.convertTo(image, CvType.CV_64FC3);
			
			//convert all rgb (from colSTI (3channel) in to an array)
			int C3_Size = (int) (image.total()*image.channels());
			double []  C3_Temp = new double [C3_Size];
			image.get(0, 0,C3_Temp);
			
			//set up array for storing values for the chromaticity MAT
			int C2_Size = (int)(chrom.total()*chrom.channels());
			double [] C2_Temp = new double [C2_Size];
			
			
			//Do the chromaticity calculation
			int counter = 1; // counter for going through C3_Temp. every 3 i is a pixel.
			int C2_counter = 0; //counter for putting values into C2_Temp
			for(int i = 0 ; i< C3_Size ; i++) {
				if (counter % 3 == 0) { // every 3 counter is 1 pixel which contains values RGB
					//Getting valuese RGB for "this" pixel
					double B =  C3_Temp [i];
					double G =  C3_Temp [i-1];
					double R =  C3_Temp [i-2];
					//chromaticity function
					double r, g; 
					if(R==0 && G==0 && B==0) { // special case when color is black
						r = 0.00;
						g = 0.00;
					}
					else { // colors other then black.
						r = R / (R+G+B);
						g = G/(R+G+B);
					}
					if(C2_counter < C2_Size) {// a checker for counter went out of bound.
						//Putting rg values into array.
						C2_Temp[C2_counter] = r;
						C2_counter ++;
						C2_Temp[C2_counter] = g;
						C2_counter++;
					}
				}
				counter++;
			}
			//putting the array of RG values into colSTI_chromaticity MAT.
			chrom.put(0, 0, C2_Temp);
			//convert back to 3 channel MAT
			image.convertTo(image, CvType.CV_8UC3);
			frames.add(chrom);
		}
		else
			System.out.println("No image is selected");
	}
	protected void diffIntersection(ArrayList<double[][]> Histogram , ArrayList<Double> I) {// array of scalar I
		for(int i = 0 ; i < totalFrame - 1 ; i++) {//going through all the frames
			double[][] previous = Histogram.get(i);
			double[][] current = Histogram.get(i+1);
			//column Histogram STI
			double sumI = 0.0;
			for(int row = 0 ; row < histBin ; row++)
				for(int col = 0 ; col < histBin ; col ++)
					 sumI+= Math.min(previous[row][col], current[row][col]);
			I.add(sumI);
		}
	}	
	protected void createHistogram(ArrayList<double[][]> Histogram , Mat chrom , int n) { // histogram is colHistogram or rowHistogram , sti should be chromaticity Mat
		// n = size of data ~ number of image rows
		histBin = (int)Math.floor(1+((double)Math.log(n)/(double)Math.log(2))); // Sturge's Rule - > N = 1+log2(n)
		int r_axis , g_axis; //col and row for histogram. where r value is row and green value is column
		double boundary = 1.00/histBin; // each histogram has n number of bin , we divide the max range by bin to find the boundary value for each bin
		
		//Creating Histogram for each frame
		int Size = (int) (chrom.total()*chrom.channels());
		double []  Temp = new double [Size]; 
		chrom.get(0, 0,Temp);//get the rg values for column(i) ~ array of values
		double [][] hist  = new double [histBin][histBin];//the "object"(2d array) to be put into array list
		//going through all the rg values in "this" column , and filling the 2-d array
		for(int rgValue = 0 ; rgValue < Size-1 ; rgValue = rgValue+2) {
			double rValue = Temp[rgValue] , gValue = Temp[rgValue+1];
			r_axis = (int)Math.floor(rValue/boundary);
			g_axis = (int)Math.floor(gValue/boundary);
			if(r_axis == histBin)//boundary case , color values toward that the floor is overflow is put into last bin
				r_axis = histBin-1;
			if(g_axis == histBin)
				g_axis = histBin-1;
			hist[r_axis][g_axis]++;
		}
		double sum = 0;
		for(int i=0 ; i<histBin ; i++)
			for(int j=0 ; j<histBin ; j++)
				sum+=hist[i][j];
		
		for(int i=0 ; i<histBin ; i++)
			for(int j=0 ; j<histBin ; j++)
				hist[i][j]= hist[i][j]/sum;
		
			Histogram.add(hist);// Adding hist object to array list histogram
	}	
////////////////////	
	
	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		if (image != null) {
			flag = 1;			
			// convert the image from RGB to grayscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
			// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, sampleRate);
            sourceDataLine.start();
            for (int col = 0; col < width; col++) {
            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
            		double signal = 0;
                	for (int row = 0; row < height; row++) {
                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
                		int time = t + col * numberOfSamplesPerColumn;
                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
                		signal += roundedImage[row][col] * ss;
                	}
                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
            	}
            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
            }
            sourceDataLine.drain();
            sourceDataLine.close();
            flag = 0;
		} else {
			System.out.println("No selected image.");
		}
	} 
}

//Removed the play image button from the .fxml
//<Button style="-fx-background-color: #119911; " mnemonicParsing="false" onAction="#playImage" prefHeight="40.0" prefWidth="100.0" text="Play Sound" textFill="black">
//<HBox.margin>
//   <Insets left="55.0" top="5.0"/>
//</HBox.margin>
//</Button>
