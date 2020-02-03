package processors;

import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.cvClearMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.OutputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.bytedeco.javacpp.helper.opencv_core.AbstractCvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvMemStorage;
import org.bytedeco.javacpp.opencv_core.CvRect;
import org.bytedeco.javacpp.opencv_core.CvSeq;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_objdetect.CvHaarClassifierCascade;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameGrabber.Exception;

import utils.Utils;

/**
 * A NiFi processor which accesses the default video camera, captures the video
 * stream, samples it into separate frames, and transfers forward for face
 * recognition.
 */
@TriggerWhenEmpty
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@Tags({ "ekstream", "video", "stream", "capturing", "sampling" })
@CapabilityDescription("Testing JavaCV api")
public class CaptureVideoDetectFaces extends EkstreamProcessor {

	/** Scale factor for face detection. */
	static final double SCALE_FACTOR = 1.5;

	/** Neighbors for face detection. */
	static final int MIN_NEIGHBOURS = 3;

	/** 1000. */
	static final int INTERVAL = 1000;

	/** Processor property. */
	public static final PropertyDescriptor CASCADE_FILE = new PropertyDescriptor.Builder().name("Cascade file.")
			.description("Specifies the cascade file to be used for face recognition.")
			.defaultValue("/home/orkes/Desktop/haarcascade_frontalface_default.xml").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	/** Processor property. */
	public static final PropertyDescriptor IMAGE_WIDTH = new PropertyDescriptor.Builder().name("Image width")
			.description("Specifies the width of images with detected faces").defaultValue("92").required(true)
			.addValidator(StandardValidators.INTEGER_VALIDATOR).build();

	/** Processor property. */
	public static final PropertyDescriptor IMAGE_HEIGHT = new PropertyDescriptor.Builder().name("Image height")
			.description("Specifies the height of images with detected faces").defaultValue("112").required(true)
			.addValidator(StandardValidators.INTEGER_VALIDATOR).build();

	/** Processor property. */
	public static final PropertyDescriptor FRAME_INTERVAL = new PropertyDescriptor.Builder()
			.name("Time interval between frames")
			.description("Specified the time interval between two captured video frames, in ms").defaultValue("1000")
			.required(true).addValidator(StandardValidators.INTEGER_VALIDATOR).build();

	/** Cascade file. */
	private static CvHaarClassifierCascade cascade;

	/** JavaCV frame grabber. */
	private static FrameGrabber grabber;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void init(final ProcessorInitializationContext aContext) {

		super.init(aContext);

		final Set<Relationship> procRels = new HashSet<>();
		procRels.add(REL_SUCCESS);
		setRelationships(Collections.unmodifiableSet(procRels));

		final List<PropertyDescriptor> supDescriptors = new ArrayList<>();
		supDescriptors.add(FRAME_INTERVAL);
		supDescriptors.add(IMAGE_WIDTH);
		supDescriptors.add(IMAGE_HEIGHT);
		supDescriptors.add(SAVE_IMAGES);
		supDescriptors.add(BENCHMARKING_DIR);
		supDescriptors.add(CASCADE_FILE);
		setProperties(Collections.unmodifiableList(supDescriptors));

		try {
			grabber = FrameGrabber.createDefault(0);
		} catch (Exception e) {
			getLogger().error("Something went wrong with the video grabber initialisation!", e);
		}

		getLogger().info("Initialision complete!");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTrigger(final ProcessContext aContext, final ProcessSession aSession) throws ProcessException {

		super.onTrigger(aContext, aSession);

		if (null == cascade) {
			cascade = new CvHaarClassifierCascade(cvLoad(aContext.getProperty(CASCADE_FILE).getValue()));
			getLogger().info("Loaded the cascade file: " + cascade.toString());
		}

		try {

			grabber.start();

			Frame frame = grabber.grab();
			IplImage image = Utils.getInstance().convertToImage(frame);

			saveInterimResults(System.currentTimeMillis() + "-received.png", image);

			ArrayList<IplImage> faces = detect(image);

			if (!faces.isEmpty()) {

				ArrayList<IplImage> resizedFaces = Utils.getInstance().resizeImages(faces,
						Integer.parseInt(aContext.getProperty(IMAGE_WIDTH).getValue()),
						Integer.parseInt(aContext.getProperty(IMAGE_HEIGHT).getValue()));

				// now transfer the cropped images forward
				for (IplImage face : resizedFaces) {

					saveInterimResults(System.currentTimeMillis() + "-resized.png", face);

					byte[] bytes = Utils.getInstance().convertToByteArray(face);

					// transfer the image
					FlowFile flowFile = aSession.create();
					flowFile = aSession.write(flowFile, new OutputStreamCallback() {

						@Override
						public void process(final OutputStream aStream) throws IOException {

							aStream.write(bytes);
						}
					});
					aSession.transfer(flowFile, REL_SUCCESS);

					// benchmarking=====================================
					benchmark(flowFile.getAttribute(CoreAttributes.UUID.key()));
					// =================================================

				}
			}

			grabber.stop();

			Thread.currentThread();
			Thread.sleep(Long.parseLong(aContext.getProperty(FRAME_INTERVAL).getValue()));

		} catch (Exception e) {
			getLogger().error("Something went wrong with the video capture!", e);
		} catch (InterruptedException e) {
			getLogger().error("Something went wrong with the threads!", e);
		} catch (IOException e) {
			getLogger().error("Something went wrong with saving the file!", e);
		} finally {
			try {
				grabber.stop();
			} catch (Exception e1) {
				getLogger().error("NESTED: Something went wrong with the video capture!", e1);
			}
		}

	}

	/**
	 * Detects faces in an input image.
	 *
	 * @param aImage input image
	 * @return an array of detected faces as images
	 */
	public ArrayList<IplImage> detect(final IplImage aImage) {

		ArrayList<IplImage> result = new ArrayList<IplImage>();

		CvMemStorage storage = AbstractCvMemStorage.create();
		CvSeq sign = cvHaarDetectObjects(aImage, cascade, storage, SCALE_FACTOR, MIN_NEIGHBOURS,
				CV_HAAR_DO_CANNY_PRUNING);

		for (int i = 0; i < sign.total(); i++) {
			CvRect r = new CvRect(cvGetSeqElem(sign, i));
			IplImage image = Utils.getInstance().cropImage(aImage, r);
			result.add(image);
		}

		cvClearMemStorage(storage);
		return result;
	}

}
