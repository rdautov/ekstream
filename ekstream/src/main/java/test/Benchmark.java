package test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class Benchmark {

    private static final String FILENAME = "/opt/nifi-1.0.1/RecogniseFaces-0523ecd1-015a-1000-3a3d-19023f1ac395-ready";


    public static void main(String[] args) throws IOException {

        BufferedReader br1 = new BufferedReader(new FileReader(FILENAME));

        String sCurrentLine;

        while ((sCurrentLine = br1.readLine()) != null) {

            String[] output = sCurrentLine.split(";");

            Double delay = Double.parseDouble(output[3]) - Double.parseDouble(output[1]);
            System.out.println(delay*10);

        }

    }




}
