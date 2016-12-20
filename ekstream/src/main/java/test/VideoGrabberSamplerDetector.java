package test;

import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.CV_32SC1;
import static org.bytedeco.javacpp.opencv_core.cvClearMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_face;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.MatVector;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;
import org.bytedeco.javacpp.presets.opencv_objdetect;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

import utils.Utils;

import org.bytedeco.javacv.FrameGrabber.Exception;

/**
 * Class for testing.
 */
public class VideoGrabberSamplerDetector {

    /** Scale factor for face detection. */
    static final double SCALE_FACTOR = 1.5;

    /** Neighbors for face detection. */
    static final int MIN_NEIGHBOURS = 3;

    /** */
    static final int INTERVAL = 1000;

    /** */
    static final int IMAGE_DIMENSION = 300;

    /** */
    public static final String XML_FILE = "/home/orkes/Desktop/haarcascade_frontalface_default.xml";

    private static OpenCVFrameConverter.ToIplImage converter;

    private static Java2DFrameConverter flatConverter;

    /** Face recognizer. */
    private static FaceRecognizer faceRecognizer;

    /**
     * @param aArgs
     * @throws Exception
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(final String[] aArgs) throws Exception, InterruptedException, IOException {

        Loader.load(opencv_objdetect.class);

        FrameGrabber grabber = FrameGrabber.createDefault(0);
        converter = new OpenCVFrameConverter.ToIplImage();
        flatConverter = new Java2DFrameConverter();

        train("/home/orkes/Desktop/training", "LBPH");

        grabber.start();

        while (true) {

            Frame frame = grabber.grab();
            IplImage image = Utils.getInstance().convertToImage(frame);

            opencv_imgcodecs.cvSaveImage(System.currentTimeMillis() + "-received.png", image);

            ArrayList<IplImage> faces = detect(image);

            if (!faces.isEmpty()) {

                //now transfer the cropped images forward
                for (IplImage face : faces) {

                    opencv_imgcodecs.cvSaveImage(System.currentTimeMillis()
                            + "-detected.png", face);
                }

                ArrayList<IplImage> resizedFaces = Utils.getInstance().resizeImages(faces, 300, 300);

                //now transfer the cropped images forward
                for (IplImage face : resizedFaces) {

                    Mat mat = Utils.getInstance().convertToMat(face);
                    mat = Utils.getInstance().convertToGrayscale(mat);

                    int[] plabel = new int[1];
                    double[] pconfidence = new double[1];

                    faceRecognizer.predict(mat, plabel, pconfidence);

                    //System.out.println("Predicted label: " + predictedLabel);
                    System.out.println("Predicted label: " + plabel[0] + " & Confidence: " + pconfidence[0]);

                    opencv_imgcodecs.cvSaveImage(System.currentTimeMillis()
                            + "-resized.png", Utils.getInstance().convertToImage(mat));
                }
            }

            Thread.currentThread();
            Thread.sleep(INTERVAL);

            //grabber.stop();

        }

    }

    /**
     * Detects faces in an input image.
     *
     * @param aImage input image
     * @return an array of detected faces as images
     */
    public static ArrayList<IplImage> detect(final IplImage aImage) {

        ArrayList<IplImage> result = new ArrayList<IplImage>();

        CvHaarClassifierCascade cascade =
                new CvHaarClassifierCascade(cvLoad(XML_FILE));
        CvMemStorage storage = AbstractCvMemStorage.create();
        CvSeq sign = cvHaarDetectObjects(aImage,
                cascade, storage, SCALE_FACTOR, MIN_NEIGHBOURS, CV_HAAR_DO_CANNY_PRUNING);

        for (int i = 0; i < sign.total(); i++) {
            CvRect r = new CvRect(cvGetSeqElem(sign, i));
            //opencv_imgproc.cvRectangle(aImage, cvPoint(r.x(), r.y()),
            //        cvPoint(r.width() + r.x(), r.height() + r.y()),
            //        AbstractCvScalar.RED, 2, LINE_AA, 0);

            IplImage image = Utils.getInstance().cropImage(aImage, r);
            result.add(image);
        }

        cvClearMemStorage(storage);
        return result;
    }

    /**
     * Trains the face recognizer.
     *
     * @param aTrainingDir directory with training images
     * @param aAlgorithm face recognition algorithm
     */
    public static void train(final String aTrainingDir, final String aAlgorithm) {

        File root = new File(aTrainingDir);

        FilenameFilter imgFilter = new FilenameFilter() {
            @Override
            public boolean accept(final File aDir, final String aName) {
                String name = aName.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
            }
        };

        File[] imageFiles = root.listFiles(imgFilter);

        MatVector images = new MatVector(imageFiles.length);

        Mat labels = new Mat(imageFiles.length, 1, CV_32SC1);
        IntBuffer labelsBuf = labels.createBuffer();

        for (int i = 0; i < imageFiles.length; i++) {

            Mat img = opencv_imgcodecs.imread(imageFiles[i].getAbsolutePath(),
                    opencv_imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);

            int label = Integer.parseInt(imageFiles[i].getName().split("\\-")[0]);
            images.put(i, img);
            labelsBuf.put(i, label);
        }

        switch (aAlgorithm) {
        case "Fisher":
            faceRecognizer = opencv_face.createFisherFaceRecognizer();
        case "Eigen":
            faceRecognizer = opencv_face.createEigenFaceRecognizer();
        case "LBPH":
            faceRecognizer = opencv_face.createLBPHFaceRecognizer();
        default:
            faceRecognizer = opencv_face.createFisherFaceRecognizer();
        }

        faceRecognizer.train(images, labels);
    }

}
