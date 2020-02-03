package test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Benchmark2 {

	private static final String DETECTION_FILENAME = "/nifi-1.0.1-standalone/benchmark/detection-f514f0a4-0159-1000-0a2a-36defe85ba98";

	private static final String RECOGNITION_FILENAME = "/nifi-1.0.1-standalone/benchmark/recognition-dcdaf9ef-0159-1000-e88f-d71797e6ce9e"

	;

	public static void main(String[] args) throws IOException {

		BufferedReader br1 = new BufferedReader(new FileReader(DETECTION_FILENAME));

		BufferedReader br2 = new BufferedReader(new FileReader(RECOGNITION_FILENAME));

		Object[] lines = br2.lines().toArray();

		String sCurrentLine;

		while ((sCurrentLine = br1.readLine()) != null) {

			String[] output1 = sCurrentLine.split(";");

			for (Object str : lines) {

				String[] output2 = str.toString().split(";");

				if (output1[0].equalsIgnoreCase(output2[0])) {
					// System.out.println(output1[0]);
					// System.out.println(output2[0]);
					// System.out.println("Detection: " + output1[1]);
					// System.out.println("Recognition: " + output2[1]);
					Double delay = Double.parseDouble(output2[1]) - Double.parseDouble(output1[1]);
					System.out.println(delay);
				}
			}

		}

	}

}
